/**
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.ankersolix.internal.handler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import com.google.gson.JsonObject;
import org.openhab.binding.ankersolix.internal.api.AnkerSolixApiClient;
import org.openhab.binding.ankersolix.internal.api.AnkerSolixAuth;
import org.openhab.binding.ankersolix.internal.api.dto.MqttInfoResponse;
import org.openhab.binding.ankersolix.internal.api.dto.SiteHomepageResponse;
import org.openhab.binding.ankersolix.internal.config.AnkerSolixAccountConfiguration;
import org.openhab.binding.ankersolix.internal.mqtt.AnkerSolixMqttClient;
import org.openhab.binding.ankersolix.internal.mqtt.MqttMessageListener;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public class AnkerSolixAccountHandler extends BaseBridgeHandler implements MqttMessageListener {

    private final Logger logger = LoggerFactory.getLogger(AnkerSolixAccountHandler.class);

    private final HttpClient httpClient;
    private @Nullable AnkerSolixApiClient apiClient;
    private @Nullable AnkerSolixMqttClient mqttClient;
    private @Nullable ScheduledFuture<?> pollingJob;
    private final Map<String, SolarbankHandler> childHandlers = new ConcurrentHashMap<>();

    private int pollCount = 0;

    public AnkerSolixAccountHandler(Bridge bridge, HttpClient httpClient) {
        super(bridge);
        this.httpClient = httpClient;
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
        scheduler.schedule(this::initializeAccount, 0, TimeUnit.SECONDS);
    }

    private void initializeAccount() {
        AnkerSolixAccountConfiguration config = getConfigAs(AnkerSolixAccountConfiguration.class);

        String email = config.email;
        String password = config.password;
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Email and password are required");
            return;
        }

        try {
            AnkerSolixAuth auth = new AnkerSolixAuth();
            AnkerSolixApiClient client = new AnkerSolixApiClient(httpClient, auth, config.server,
                    config.country, email, password);
            this.apiClient = client;

            client.authenticate();
            updateStatus(ThingStatus.ONLINE);

            // Log available sites and devices to help with configuration
            try {
                var siteList = client.getSiteList();
                if (siteList.siteList.isEmpty()) {
                    logger.info("No Anker Solix sites found for this account");
                } else {
                    logger.info("Found {} Anker Solix site(s):", siteList.siteList.size());
                    for (var site : siteList.siteList) {
                        logger.info("  Site: name='{}', siteId='{}'", site.siteName, site.siteId);
                        try {
                            var homepage = client.getSiteHomepage(site.siteId);
                            for (var device : homepage.solarbankList) {
                                logger.info("    Device: name='{}', deviceSn='{}', firmware='{}'", device.deviceName,
                                        device.deviceSn, device.swVersion);
                            }
                        } catch (Exception e) {
                            logger.debug("Failed to get devices for site {}: {}", site.siteId, e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Failed to retrieve site list: {}", e.getMessage());
            }

            // Start REST polling
            int interval = Math.max(60, config.restPollingInterval);
            pollingJob = scheduler.scheduleWithFixedDelay(this::poll, 5, interval, TimeUnit.SECONDS);

            // Initialize MQTT if enabled
            if (config.enableMqtt) {
                scheduler.schedule(this::initializeMqtt, 10, TimeUnit.SECONDS);
            }

        } catch (Exception e) {
            logger.error("Failed to initialize Anker Solix account", e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Authentication failed: " + e.getMessage());
        }
    }

    private void initializeMqtt() {
        AnkerSolixApiClient client = this.apiClient;
        if (client == null) {
            return;
        }

        try {
            MqttInfoResponse mqttInfo = client.getMqttInfo();
            if (mqttInfo.endpointAddr.isBlank()) {
                logger.warn("MQTT endpoint not available");
                return;
            }

            AnkerSolixMqttClient mqtt = new AnkerSolixMqttClient(this, scheduler);
            mqtt.connect(mqttInfo);
            this.mqttClient = mqtt;

            // Subscribe and trigger for all registered child devices
            for (Map.Entry<String, SolarbankHandler> entry : childHandlers.entrySet()) {
                mqtt.subscribeAndTrigger(mqttInfo.appName, "A17C5", entry.getKey());
            }

            logger.info("MQTT client connected to {}", mqttInfo.endpointAddr);
        } catch (Exception e) {
            logger.warn("Failed to initialize MQTT: {}", e.getMessage());
        }
    }

    private void poll() {
        AnkerSolixApiClient client = this.apiClient;
        if (client == null) {
            return;
        }

        try {
            if (!client.isAuthenticated()) {
                client.authenticate();
            }

            for (Map.Entry<String, SolarbankHandler> entry : childHandlers.entrySet()) {
                SolarbankHandler handler = entry.getValue();
                String siteId = handler.getSiteId();
                if (siteId != null) {
                    // Use HES endpoint for Solarbank 3 Pro (get_site_homepage doesn't return
                    // device data for HES systems)
                    try {
                        JsonObject hesData = client.getSystemRunningInfo(siteId);
                        handler.updateFromHes(hesData);
                    } catch (Exception e) {
                        logger.debug("Failed to get HES system running info: {}", e.getMessage());
                        // Fallback: try legacy homepage endpoint
                        try {
                            SiteHomepageResponse homepage = client.getSiteHomepage(siteId);
                            handler.updateFromRest(homepage);
                        } catch (Exception e2) {
                            logger.debug("Fallback homepage also failed: {}", e2.getMessage());
                        }
                    }

                    // Every 5th poll: get device params and device attributes
                    if (pollCount % 5 == 0) {
                        try {
                            var deviceParams = client.getDeviceParam(siteId, 18);
                            handler.updateSettingsFromRest(deviceParams);
                        } catch (Exception e) {
                            logger.debug("Failed to get device params: {}", e.getMessage());
                        }

                        // Poll device attributes for REST control channels
                        String sn = handler.getDeviceSn();
                        if (sn != null) {
                            try {
                                var attrs = client.getDeviceAttributes(sn, "power_limit",
                                        "pv_power_limit", "ac_power_limit", "switch_0w");
                                handler.updateRestControlFromAttributes(attrs);
                            } catch (Exception e) {
                                logger.debug("Failed to get device attributes: {}", e.getMessage());
                            }
                        }

                        // Poll home load schedule (param_type=6)
                        try {
                            var scheduleParams = client.getDeviceParam(siteId, 6);
                            handler.updateHomeLoadFromParam(scheduleParams);
                        } catch (Exception e) {
                            logger.debug("Failed to get home load params: {}", e.getMessage());
                        }
                    }
                }
            }

            pollCount++;
            updateStatus(ThingStatus.ONLINE);
        } catch (Exception e) {
            logger.warn("Polling error: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Bridge has no writable channels
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> job = pollingJob;
        if (job != null) {
            job.cancel(true);
            pollingJob = null;
        }

        AnkerSolixMqttClient mqtt = mqttClient;
        if (mqtt != null) {
            mqtt.disconnect();
            mqttClient = null;
        }

        childHandlers.clear();
    }

    // --- Child handler management ---

    public void registerChildHandler(String deviceSn, SolarbankHandler handler) {
        childHandlers.put(deviceSn, handler);
        logger.debug("Registered child handler for device {}", deviceSn);

        // If MQTT is already connected, subscribe for this device
        AnkerSolixMqttClient mqtt = mqttClient;
        if (mqtt != null && mqtt.isConnected()) {
            try {
                AnkerSolixApiClient client = apiClient;
                if (client != null) {
                    MqttInfoResponse info = client.getMqttInfo();
                    mqtt.subscribeAndTrigger(info.appName, "A17C5", deviceSn);
                }
            } catch (Exception e) {
                logger.warn("Failed to subscribe MQTT for device {}: {}", deviceSn, e.getMessage());
            }
        }
    }

    public void unregisterChildHandler(String deviceSn) {
        childHandlers.remove(deviceSn);
        logger.debug("Unregistered child handler for device {}", deviceSn);
    }

    public @Nullable AnkerSolixApiClient getApiClient() {
        return apiClient;
    }

    public @Nullable AnkerSolixMqttClient getMqttClient() {
        return mqttClient;
    }

    // --- MqttMessageListener ---

    @Override
    public void onDeviceUpdate(String deviceSn, Map<String, Object> fields) {
        SolarbankHandler handler = childHandlers.get(deviceSn);
        if (handler != null) {
            handler.updateFromMqtt(fields);
        } else {
            logger.trace("Received MQTT data for unregistered device: {}", deviceSn);
        }
    }
}
