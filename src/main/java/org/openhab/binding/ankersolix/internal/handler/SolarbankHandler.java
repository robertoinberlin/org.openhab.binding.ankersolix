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

import static org.openhab.binding.ankersolix.internal.AnkerSolixBindingConstants.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;
import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.ankersolix.internal.api.AnkerSolixApiClient;
import org.openhab.binding.ankersolix.internal.api.dto.SiteHomepageResponse;
import org.openhab.binding.ankersolix.internal.config.SolarbankConfiguration;
import org.openhab.binding.ankersolix.internal.mqtt.AnkerSolixMqttClient;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

@NonNullByDefault
public class SolarbankHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(SolarbankHandler.class);

    private static final int COMMAND_GRACE_PERIOD_SECONDS = 300;

    private @Nullable String siteId;
    private @Nullable String deviceSn;
    private final Map<String, Instant> recentCommands = new ConcurrentHashMap<>();

    public SolarbankHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        SolarbankConfiguration config = getConfigAs(SolarbankConfiguration.class);

        this.siteId = config.siteId;
        this.deviceSn = config.deviceSn;

        if (siteId == null || siteId.isBlank() || deviceSn == null || deviceSn.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Site ID and Device Serial Number are required");
            return;
        }

        Bridge bridge = getBridge();
        if (bridge == null || !(bridge.getHandler() instanceof AnkerSolixAccountHandler bridgeHandler)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return;
        }

        bridgeHandler.registerChildHandler(deviceSn, this);
        updateStatus(ThingStatus.UNKNOWN);
    }

    @Override
    public void dispose() {
        String sn = deviceSn;
        Bridge bridge = getBridge();
        if (sn != null && bridge != null && bridge.getHandler() instanceof AnkerSolixAccountHandler bridgeHandler) {
            bridgeHandler.unregisterChildHandler(sn);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            return;
        }

        String groupId = channelUID.getGroupId();
        if (groupId == null) {
            return;
        }

        String channelId = channelUID.getIdWithoutGroup();
        AnkerSolixAccountHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler == null) {
            return;
        }

        String sn = deviceSn;
        if (sn == null) {
            return;
        }

        // REST Control group - works without MQTT
        if (GROUP_REST_CONTROL.equals(groupId)) {
            handleRestControlCommand(channelId, command, bridgeHandler, sn);
            return;
        }

        if (!GROUP_SETTINGS.equals(groupId)) {
            return;
        }

        AnkerSolixMqttClient mqtt = bridgeHandler.getMqttClient();
        if (mqtt == null || !mqtt.isConnected()) {
            logger.warn("MQTT not connected - settings commands require MQTT to be enabled. Channel: {}", channelId);
            return;
        }

        try {
            switch (channelId) {
                case CHANNEL_MIN_SOC:
                    if (command instanceof DecimalType decimal) {
                        mqtt.sendMinSocCommand(sn, "A17C5", decimal.intValue());
                    } else if (command instanceof QuantityType<?> qty) {
                        mqtt.sendMinSocCommand(sn, "A17C5", qty.intValue());
                    }
                    break;
                case CHANNEL_MAX_LOAD:
                    if (command instanceof QuantityType<?> qty) {
                        mqtt.sendMaxLoadCommand(sn, "A17C5", qty.intValue());
                    } else if (command instanceof DecimalType decimal) {
                        mqtt.sendMaxLoadCommand(sn, "A17C5", decimal.intValue());
                    }
                    break;
                case CHANNEL_AC_SOCKET:
                    if (command instanceof OnOffType) {
                        mqtt.sendAcSocketCommand(sn, "A17C5", command == OnOffType.ON);
                    }
                    break;
                case CHANNEL_DISABLE_GRID_EXPORT:
                    if (command instanceof OnOffType) {
                        mqtt.sendDisableGridExportCommand(sn, "A17C5", command == OnOffType.ON);
                    }
                    break;
                case CHANNEL_AC_INPUT_LIMIT:
                    if (command instanceof QuantityType<?> qty) {
                        mqtt.sendAcInputLimitCommand(sn, "A17C5", qty.intValue());
                    } else if (command instanceof DecimalType decimal) {
                        mqtt.sendAcInputLimitCommand(sn, "A17C5", decimal.intValue());
                    }
                    break;
                case CHANNEL_PV_LIMIT:
                    if (command instanceof QuantityType<?> qty) {
                        mqtt.sendPvLimitCommand(sn, "A17C5", qty.intValue());
                    } else if (command instanceof DecimalType decimal) {
                        mqtt.sendPvLimitCommand(sn, "A17C5", decimal.intValue());
                    }
                    break;
                case CHANNEL_LIGHT_SWITCH:
                    if (command instanceof OnOffType) {
                        mqtt.sendLightSwitchCommand(sn, "A17C5", command == OnOffType.ON);
                    }
                    break;
                default:
                    logger.debug("Unsupported command channel: {}", channelId);
            }
        } catch (Exception e) {
            logger.warn("Failed to send command for {}: {}", channelId, e.getMessage());
        }
    }

    /**
     * Update channels from REST homepage response.
     * This provides power overview, battery SOC, daily energy, and device info.
     * Power values are 1-5 minute averages (not real-time like MQTT).
     */
    public void updateFromRest(SiteHomepageResponse homepage) {
        String sn = deviceSn;
        if (sn == null) {
            return;
        }

        // Site-level power data (available even without per-device match)
        SiteHomepageResponse.HomeInfo home = homepage.homeInfo;
        updateState(new ChannelUID(getThing().getUID(), GROUP_POWER, CHANNEL_PV_POWER),
                new QuantityType<>(parseDouble(home.solarPower), Units.WATT));
        updateState(new ChannelUID(getThing().getUID(), GROUP_POWER, CHANNEL_HOME_DEMAND),
                new QuantityType<>(parseDouble(home.homeUsagePower), Units.WATT));
        updateState(new ChannelUID(getThing().getUID(), GROUP_BATTERY, CHANNEL_BATTERY_POWER),
                new QuantityType<>(parseDouble(home.batteryPower), Units.WATT));
        updateState(new ChannelUID(getThing().getUID(), GROUP_GRID, CHANNEL_GRID_POWER),
                new QuantityType<>(parseDouble(home.gridPower), Units.WATT));

        // Find this device in the solarbank list for per-device data
        for (SiteHomepageResponse.SolarbankInfo sb : homepage.solarbankList) {
            if (sn.equals(sb.deviceSn)) {
                // Battery SOC
                updateState(new ChannelUID(getThing().getUID(), GROUP_BATTERY, CHANNEL_BATTERY_SOC),
                        new QuantityType<>(parseDouble(sb.batterySoc), Units.PERCENT));

                // Per-device power (if available)
                double pvPower = parseDouble(sb.pvPower);
                if (pvPower > 0) {
                    updateState(new ChannelUID(getThing().getUID(), GROUP_POWER, CHANNEL_PV_POWER),
                            new QuantityType<>(pvPower, Units.WATT));
                }
                double outputPower = parseDouble(sb.outputPower);
                if (outputPower > 0) {
                    updateState(new ChannelUID(getThing().getUID(), GROUP_POWER, CHANNEL_OUTPUT_POWER),
                            new QuantityType<>(outputPower, Units.WATT));
                }

                // Online status
                updateState(new ChannelUID(getThing().getUID(), GROUP_INFO, CHANNEL_ONLINE_STATUS),
                        sb.wifiOnline ? OnOffType.ON : OnOffType.OFF);

                // Firmware version
                if (!sb.swVersion.isEmpty()) {
                    updateState(new ChannelUID(getThing().getUID(), GROUP_INFO, CHANNEL_FIRMWARE_VERSION),
                            new StringType(sb.swVersion));
                }

                updateStatus(ThingStatus.ONLINE);
                break;
            }
        }

        // Daily energy data from site-level info
        SiteHomepageResponse.EnergyInfo energy = homepage.energyInfo;
        updateEnergyChannel(GROUP_ENERGY, CHANNEL_DAILY_SOLAR, parseDouble(energy.solarProduction));
        updateEnergyChannel(GROUP_ENERGY, CHANNEL_DAILY_CHARGE, parseDouble(energy.batteryCharge));
        updateEnergyChannel(GROUP_ENERGY, CHANNEL_DAILY_DISCHARGE, parseDouble(energy.batteryDischarge));
        updateEnergyChannel(GROUP_ENERGY, CHANNEL_DAILY_GRID_IMPORT, parseDouble(energy.gridImport));
        updateEnergyChannel(GROUP_ENERGY, CHANNEL_DAILY_GRID_EXPORT, parseDouble(energy.gridExport));

        // Last update timestamp
        updateState(new ChannelUID(getThing().getUID(), GROUP_INFO, CHANNEL_LAST_UPDATE),
                new DateTimeType(ZonedDateTime.now()));
    }

    /**
     * Update channels from HES (Home Energy System) running info response.
     * This is the primary REST data source for Solarbank 3 Pro devices.
     */
    public void updateFromHes(JsonObject hesData) {

        // Try to extract power values from known HES fields
        updateHesNumber(hesData, "solar_power", GROUP_POWER, CHANNEL_PV_POWER, Units.WATT);
        updateHesNumber(hesData, "photovoltaic_power", GROUP_POWER, CHANNEL_PV_POWER, Units.WATT);
        updateHesNumber(hesData, "pv_power", GROUP_POWER, CHANNEL_PV_POWER, Units.WATT);

        updateHesNumber(hesData, "output_power", GROUP_POWER, CHANNEL_OUTPUT_POWER, Units.WATT);
        updateHesNumber(hesData, "home_load_power", GROUP_POWER, CHANNEL_OUTPUT_POWER, Units.WATT);

        updateHesNumber(hesData, "ac_output_power", GROUP_POWER, CHANNEL_AC_OUTPUT_POWER, Units.WATT);
        updateHesNumber(hesData, "home_usage_power", GROUP_POWER, CHANNEL_HOME_DEMAND, Units.WATT);
        updateHesNumber(hesData, "home_demand", GROUP_POWER, CHANNEL_HOME_DEMAND, Units.WATT);

        updateHesNumber(hesData, "battery_power", GROUP_BATTERY, CHANNEL_BATTERY_POWER, Units.WATT);
        updateHesNumber(hesData, "battery_soc", GROUP_BATTERY, CHANNEL_BATTERY_SOC, Units.PERCENT);
        updateHesNumber(hesData, "soc", GROUP_BATTERY, CHANNEL_BATTERY_SOC, Units.PERCENT);

        updateHesNumber(hesData, "grid_power", GROUP_GRID, CHANNEL_GRID_POWER, Units.WATT);
        updateHesNumber(hesData, "grid_to_home_power", GROUP_GRID, CHANNEL_GRID_POWER, Units.WATT);

        // Energy
        updateHesNumber(hesData, "solar_production", GROUP_ENERGY, CHANNEL_DAILY_SOLAR, Units.KILOWATT_HOUR);
        updateHesNumber(hesData, "battery_charge", GROUP_ENERGY, CHANNEL_DAILY_CHARGE, Units.KILOWATT_HOUR);
        updateHesNumber(hesData, "battery_discharge", GROUP_ENERGY, CHANNEL_DAILY_DISCHARGE, Units.KILOWATT_HOUR);
        updateHesNumber(hesData, "grid_import", GROUP_ENERGY, CHANNEL_DAILY_GRID_IMPORT, Units.KILOWATT_HOUR);
        updateHesNumber(hesData, "grid_export", GROUP_ENERGY, CHANNEL_DAILY_GRID_EXPORT, Units.KILOWATT_HOUR);

        // Firmware / device info from nested objects
        if (hesData.has("firmware_version")) {
            updateState(new ChannelUID(getThing().getUID(), GROUP_INFO, CHANNEL_FIRMWARE_VERSION),
                    new StringType(hesData.get("firmware_version").getAsString()));
        }
        if (hesData.has("sw_version")) {
            updateState(new ChannelUID(getThing().getUID(), GROUP_INFO, CHANNEL_FIRMWARE_VERSION),
                    new StringType(hesData.get("sw_version").getAsString()));
        }

        // Last update timestamp
        updateState(new ChannelUID(getThing().getUID(), GROUP_INFO, CHANNEL_LAST_UPDATE),
                new DateTimeType(ZonedDateTime.now()));
        updateState(new ChannelUID(getThing().getUID(), GROUP_INFO, CHANNEL_ONLINE_STATUS), OnOffType.ON);
        updateStatus(ThingStatus.ONLINE);
    }

    private void updateHesNumber(JsonObject data, String jsonKey, String group, String channel,
            javax.measure.Unit<?> unit) {
        if (data.has(jsonKey)) {
            try {
                double value = data.get(jsonKey).getAsDouble();
                updateState(new ChannelUID(getThing().getUID(), group, channel), new QuantityType<>(value, unit));
            } catch (Exception e) {
                logger.trace("Failed to parse HES field {}: {}", jsonKey, e.getMessage());
            }
        }
    }

    /**
     * Update settings channels from REST device params response.
     * The API wraps the data as: {"param_data": "{\"soc_list\":[...]}"}
     */
    public void updateSettingsFromRest(JsonObject params) {
        JsonObject data = unwrapParamData(params);

        if (data.has("soc_list")) {
            try {
                var socList = data.getAsJsonArray("soc_list");
                for (var element : socList) {
                    var socObj = element.getAsJsonObject();
                    if (socObj.has("is_selected") && socObj.get("is_selected").getAsInt() == 1) {
                        int soc = socObj.get("soc").getAsInt();
                        updateState(new ChannelUID(getThing().getUID(), GROUP_SETTINGS, CHANNEL_MIN_SOC),
                                new QuantityType<>(soc, Units.PERCENT));
                    }
                }
            } catch (Exception e) {
                logger.debug("Failed to parse SOC list from device params: {}", e.getMessage());
            }
        }

        if (data.has("switch_0w")) {
            boolean exportDisabled = data.get("switch_0w").getAsInt() != 0;
            updateState(new ChannelUID(getThing().getUID(), GROUP_REST_CONTROL, CHANNEL_REST_GRID_EXPORT),
                    exportDisabled ? OnOffType.OFF : OnOffType.ON);
        }
    }

    /**
     * Update channels from MQTT telemetry data.
     */
    public void updateFromMqtt(Map<String, Object> fields) {
        // Power channels
        updateMqttPower(fields, "photovoltaic_power", GROUP_POWER, CHANNEL_PV_POWER);
        updateMqttPower(fields, "output_power", GROUP_POWER, CHANNEL_OUTPUT_POWER);
        updateMqttPower(fields, "ac_output_power_signed", GROUP_POWER, CHANNEL_AC_OUTPUT_POWER);
        updateMqttPower(fields, "home_demand", GROUP_POWER, CHANNEL_HOME_DEMAND);

        // Solar strings
        updateMqttPower(fields, "pv_1_power", GROUP_SOLAR, CHANNEL_PV1_POWER);
        updateMqttPower(fields, "pv_2_power", GROUP_SOLAR, CHANNEL_PV2_POWER);
        updateMqttPower(fields, "pv_3_power", GROUP_SOLAR, CHANNEL_PV3_POWER);
        updateMqttPower(fields, "pv_4_power", GROUP_SOLAR, CHANNEL_PV4_POWER);

        // Battery
        updateMqttPower(fields, "battery_power_signed", GROUP_BATTERY, CHANNEL_BATTERY_POWER);
        if (fields.containsKey("battery_soc")) {
            double soc = ((Number) fields.get("battery_soc")).doubleValue();
            updateState(new ChannelUID(getThing().getUID(), GROUP_BATTERY, CHANNEL_BATTERY_SOC),
                    new QuantityType<>(soc, Units.PERCENT));
        }
        if (fields.containsKey("temperature")) {
            double temp = ((Number) fields.get("temperature")).doubleValue();
            updateState(new ChannelUID(getThing().getUID(), GROUP_BATTERY, CHANNEL_TEMPERATURE),
                    new QuantityType<>(temp, SIUnits.CELSIUS));
        }

        // Grid
        updateMqttPower(fields, "grid_power_signed", GROUP_GRID, CHANNEL_GRID_POWER);

        // Cumulative energy
        updateMqttEnergy(fields, "pv_yield", GROUP_ENERGY, CHANNEL_PV_YIELD);
        updateMqttEnergy(fields, "charged_energy", GROUP_ENERGY, CHANNEL_CHARGED_ENERGY);
        updateMqttEnergy(fields, "discharged_energy", GROUP_ENERGY, CHANNEL_DISCHARGED_ENERGY);

        // Settings state from telemetry
        if (fields.containsKey("min_soc")) {
            updateState(new ChannelUID(getThing().getUID(), GROUP_SETTINGS, CHANNEL_MIN_SOC),
                    new QuantityType<>(((Number) fields.get("min_soc")).doubleValue(), Units.PERCENT));
        }
        if (fields.containsKey("max_load")) {
            updateState(new ChannelUID(getThing().getUID(), GROUP_SETTINGS, CHANNEL_MAX_LOAD),
                    new QuantityType<>(((Number) fields.get("max_load")).doubleValue(), Units.WATT));
        }
        if (fields.containsKey("ac_socket_switch")) {
            boolean on = ((Number) fields.get("ac_socket_switch")).intValue() != 0;
            updateState(new ChannelUID(getThing().getUID(), GROUP_SETTINGS, CHANNEL_AC_SOCKET),
                    on ? OnOffType.ON : OnOffType.OFF);
        }
        if (fields.containsKey("grid_export_disabled")) {
            boolean disabled = ((Number) fields.get("grid_export_disabled")).intValue() != 0;
            updateState(new ChannelUID(getThing().getUID(), GROUP_SETTINGS, CHANNEL_DISABLE_GRID_EXPORT),
                    disabled ? OnOffType.ON : OnOffType.OFF);
        }
        if (fields.containsKey("ac_input_limit")) {
            updateState(new ChannelUID(getThing().getUID(), GROUP_SETTINGS, CHANNEL_AC_INPUT_LIMIT),
                    new QuantityType<>(((Number) fields.get("ac_input_limit")).doubleValue(), Units.WATT));
        }
        if (fields.containsKey("pv_limit")) {
            updateState(new ChannelUID(getThing().getUID(), GROUP_SETTINGS, CHANNEL_PV_LIMIT),
                    new QuantityType<>(((Number) fields.get("pv_limit")).doubleValue(), Units.WATT));
        }
        if (fields.containsKey("light_off_switch")) {
            boolean on = ((Number) fields.get("light_off_switch")).intValue() != 0;
            updateState(new ChannelUID(getThing().getUID(), GROUP_SETTINGS, CHANNEL_LIGHT_SWITCH),
                    on ? OnOffType.ON : OnOffType.OFF);
        }

        // Last update
        updateState(new ChannelUID(getThing().getUID(), GROUP_INFO, CHANNEL_LAST_UPDATE),
                new DateTimeType(ZonedDateTime.now()));

        updateStatus(ThingStatus.ONLINE);
    }

    /**
     * Update REST control channel states from device attributes.
     * The API returns: {"device_sn":"...", "attributes":{"pv_power_limit":3600,...}}
     */
    public void updateRestControlFromAttributes(JsonObject data) {
        // Unwrap the "attributes" object if present
        JsonObject attrs = data.has("attributes") ? data.getAsJsonObject("attributes") : data;

        if (attrs.has("power_limit") && !wasRecentlyCommanded(CHANNEL_REST_OUTPUT_LIMIT)) {
            updateState(new ChannelUID(getThing().getUID(), GROUP_REST_CONTROL, CHANNEL_REST_OUTPUT_LIMIT),
                    new QuantityType<>(attrs.get("power_limit").getAsDouble(), Units.WATT));
        }
        if (attrs.has("pv_power_limit") && !wasRecentlyCommanded(CHANNEL_REST_PV_LIMIT)) {
            updateState(new ChannelUID(getThing().getUID(), GROUP_REST_CONTROL, CHANNEL_REST_PV_LIMIT),
                    new QuantityType<>(attrs.get("pv_power_limit").getAsDouble(), Units.WATT));
        }
        if (attrs.has("ac_power_limit") && !wasRecentlyCommanded(CHANNEL_REST_AC_LIMIT)) {
            updateState(new ChannelUID(getThing().getUID(), GROUP_REST_CONTROL, CHANNEL_REST_AC_LIMIT),
                    new QuantityType<>(attrs.get("ac_power_limit").getAsDouble(), Units.WATT));
        }
        if (attrs.has("switch_0w") && !wasRecentlyCommanded(CHANNEL_REST_GRID_EXPORT)) {
            // switch_0w: 0 = grid export ON, 1 = grid export OFF (inverted)
            boolean exportEnabled = attrs.get("switch_0w").getAsInt() == 0;
            updateState(new ChannelUID(getThing().getUID(), GROUP_REST_CONTROL, CHANNEL_REST_GRID_EXPORT),
                    exportEnabled ? OnOffType.ON : OnOffType.OFF);
        }
    }

    /**
     * Update home load channel state from device param response.
     * The API wraps the data as: {"param_data": "{\"default_home_load\":200,...}"}
     */
    public void updateHomeLoadFromParam(JsonObject paramResponse) {
        if (wasRecentlyCommanded(CHANNEL_REST_HOME_LOAD)) {
            return;
        }

        JsonObject data = unwrapParamData(paramResponse);

        if (data.has("default_home_load")) {
            double homeLoad = data.get("default_home_load").getAsDouble();
            updateState(new ChannelUID(getThing().getUID(), GROUP_REST_CONTROL, CHANNEL_REST_HOME_LOAD),
                    new QuantityType<>(homeLoad, Units.WATT));
        }
    }

    /**
     * Unwrap param_data: the API returns {"param_data": "{json string}"}.
     * If param_data is a JSON string, parse it. Otherwise return the object as-is.
     */
    private JsonObject unwrapParamData(JsonObject response) {
        if (response.has("param_data")) {
            try {
                String paramDataStr = response.get("param_data").getAsString();
                return new com.google.gson.JsonParser().parse(paramDataStr).getAsJsonObject();
            } catch (Exception e) {
                logger.debug("Failed to unwrap param_data: {}", e.getMessage());
            }
        }
        return response;
    }

    public @Nullable String getSiteId() {
        return siteId;
    }

    public @Nullable String getDeviceSn() {
        return deviceSn;
    }

    // --- Private helpers ---

    private void handleRestControlCommand(String channelId, Command command,
            AnkerSolixAccountHandler bridgeHandler, String sn) {
        AnkerSolixApiClient apiClient = bridgeHandler.getApiClient();
        if (apiClient == null) {
            logger.warn("API client not available for REST control command");
            return;
        }

        String site = siteId;
        if (site == null) {
            logger.warn("Site ID not configured, cannot send REST control command");
            return;
        }

        try {
            switch (channelId) {
                case CHANNEL_REST_HOME_LOAD:
                    int watts = extractWatts(command);
                    if (watts >= 0) {
                        apiClient.setHomeLoad(site, watts);
                        markCommandSent(CHANNEL_REST_HOME_LOAD);
                        updateState(new ChannelUID(getThing().getUID(), GROUP_REST_CONTROL, CHANNEL_REST_HOME_LOAD),
                                new QuantityType<>(watts, Units.WATT));
                        logger.debug("Set home load to {} W via REST", watts);
                    }
                    break;

                case CHANNEL_REST_OUTPUT_LIMIT:
                    int outputLimit = extractWatts(command);
                    if (outputLimit >= 0) {
                        apiClient.setDeviceAttributes(sn, Map.of("power_limit", outputLimit));
                        markCommandSent(CHANNEL_REST_OUTPUT_LIMIT);
                        updateState(
                                new ChannelUID(getThing().getUID(), GROUP_REST_CONTROL, CHANNEL_REST_OUTPUT_LIMIT),
                                new QuantityType<>(outputLimit, Units.WATT));
                        logger.debug("Set output limit to {} W via REST", outputLimit);
                    }
                    break;

                case CHANNEL_REST_PV_LIMIT:
                    int pvLimit = extractWatts(command);
                    if (pvLimit >= 0) {
                        apiClient.setDeviceAttributes(sn, Map.of("pv_power_limit", pvLimit));
                        markCommandSent(CHANNEL_REST_PV_LIMIT);
                        updateState(new ChannelUID(getThing().getUID(), GROUP_REST_CONTROL, CHANNEL_REST_PV_LIMIT),
                                new QuantityType<>(pvLimit, Units.WATT));
                        logger.debug("Set PV input limit to {} W via REST", pvLimit);
                    }
                    break;

                case CHANNEL_REST_AC_LIMIT:
                    int acLimit = extractWatts(command);
                    if (acLimit >= 0) {
                        apiClient.setDeviceAttributes(sn, Map.of("ac_power_limit", acLimit));
                        markCommandSent(CHANNEL_REST_AC_LIMIT);
                        updateState(new ChannelUID(getThing().getUID(), GROUP_REST_CONTROL, CHANNEL_REST_AC_LIMIT),
                                new QuantityType<>(acLimit, Units.WATT));
                        logger.debug("Set AC input limit to {} W via REST", acLimit);
                    }
                    break;

                case CHANNEL_REST_GRID_EXPORT:
                    if (command instanceof OnOffType) {
                        // switch_0w: 0 = export ON, 1 = export OFF (inverted!)
                        int switchVal = command == OnOffType.ON ? 0 : 1;
                        apiClient.setDeviceAttributes(sn, Map.of("switch_0w", switchVal));
                        markCommandSent(CHANNEL_REST_GRID_EXPORT);
                        updateState(
                                new ChannelUID(getThing().getUID(), GROUP_REST_CONTROL, CHANNEL_REST_GRID_EXPORT),
                                (OnOffType) command);
                        logger.debug("Set grid export {} via REST", command == OnOffType.ON ? "enabled" : "disabled");
                    }
                    break;

                default:
                    logger.debug("Unsupported REST control channel: {}", channelId);
            }
        } catch (Exception e) {
            logger.warn("Failed to send REST control command for {}: {}", channelId, e.getMessage());
        }
    }

    private void markCommandSent(String channelId) {
        recentCommands.put(channelId, Instant.now());
    }

    private boolean wasRecentlyCommanded(String channelId) {
        Instant sent = recentCommands.get(channelId);
        return sent != null && Instant.now().isBefore(sent.plusSeconds(COMMAND_GRACE_PERIOD_SECONDS));
    }

    private int extractWatts(Command command) {
        if (command instanceof QuantityType<?> qty) {
            return qty.intValue();
        } else if (command instanceof DecimalType decimal) {
            return decimal.intValue();
        }
        logger.warn("Cannot extract watts from command type: {}", command.getClass().getSimpleName());
        return -1;
    }

    private @Nullable AnkerSolixAccountHandler getBridgeHandler() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof AnkerSolixAccountHandler handler) {
            return handler;
        }
        return null;
    }

    private void updateMqttPower(Map<String, Object> fields, String fieldName, String group, String channel) {
        if (fields.containsKey(fieldName)) {
            double value = ((Number) fields.get(fieldName)).doubleValue();
            updateState(new ChannelUID(getThing().getUID(), group, channel),
                    new QuantityType<>(value, Units.WATT));
        }
    }

    private void updateMqttEnergy(Map<String, Object> fields, String fieldName, String group, String channel) {
        if (fields.containsKey(fieldName)) {
            double value = ((Number) fields.get(fieldName)).doubleValue();
            updateState(new ChannelUID(getThing().getUID(), group, channel),
                    new QuantityType<>(value, Units.KILOWATT_HOUR));
        }
    }

    private void updatePowerChannel(String group, String channel, double value) {
        if (CHANNEL_BATTERY_SOC.equals(channel)) {
            updateState(new ChannelUID(getThing().getUID(), group, channel),
                    new QuantityType<>(value, Units.PERCENT));
        } else if (CHANNEL_ONLINE_STATUS.equals(channel)) {
            updateState(new ChannelUID(getThing().getUID(), group, channel),
                    value > 0 ? OnOffType.ON : OnOffType.OFF);
        } else {
            updateState(new ChannelUID(getThing().getUID(), group, channel),
                    new QuantityType<>(value, Units.WATT));
        }
    }

    private void updateEnergyChannel(String group, String channel, double value) {
        updateState(new ChannelUID(getThing().getUID(), group, channel),
                new QuantityType<>(value, Units.KILOWATT_HOUR));
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
