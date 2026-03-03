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
package org.openhab.binding.ankersolix.internal.mqtt;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.openhab.binding.ankersolix.internal.api.dto.MqttInfoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

@NonNullByDefault
public class AnkerSolixMqttClient implements MqttCallbackExtended {

    private static final Logger logger = LoggerFactory.getLogger(AnkerSolixMqttClient.class);
    private static final int REALTIME_TRIGGER_INTERVAL_SECONDS = 120;
    private static final int REALTIME_TRIGGER_TIMEOUT_SECONDS = 180;

    private final MqttMessageListener listener;
    private final MqttMessageParser parser;
    private final MqttCommandBuilder commandBuilder;
    private final ScheduledExecutorService scheduler;
    private final Gson gson;

    private @Nullable MqttClient client;
    private final List<ScheduledFuture<?>> triggerJobs = new ArrayList<>();
    private @Nullable String appName;

    /** Tracks device subscriptions so they can be restored after reconnection. */
    private final List<DeviceSubscription> subscriptions = new ArrayList<>();

    private record DeviceSubscription(String appName, String productCode, String deviceSn) {
    }

    public AnkerSolixMqttClient(MqttMessageListener listener, ScheduledExecutorService scheduler) {
        this.listener = listener;
        this.parser = new MqttMessageParser();
        this.commandBuilder = new MqttCommandBuilder();
        this.scheduler = scheduler;
        this.gson = new Gson();
    }

    /**
     * Connect to the Anker cloud MQTT broker using certificates from the REST API.
     */
    public void connect(MqttInfoResponse mqttInfo) throws Exception {
        this.appName = mqttInfo.appName;

        String brokerUrl = "ssl://" + mqttInfo.endpointAddr + ":8883";
        String clientId = mqttInfo.thingName + "_" + String.format("%05d", (int) (Math.random() * 10000));

        SSLSocketFactory sslFactory = createSslSocketFactory(
                mqttInfo.awsRootCa1Pem, mqttInfo.certificatePem, mqttInfo.privateKey);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setSocketFactory(sslFactory);
        // Set the real ssl:// URI via setServerURIs to bypass the MqttClient constructor's
        // ServiceLoader-based URI scheme validation, which fails in OSGi environments
        // because the embedded Paho JAR's META-INF/services entries are not visible.
        options.setServerURIs(new String[] { brokerUrl });
        options.setCleanSession(true);
        options.setKeepAliveInterval(60);
        options.setConnectionTimeout(30);
        options.setAutomaticReconnect(true);

        // Use tcp:// placeholder in constructor to avoid ServiceLoader validation;
        // the actual ssl:// URI from setServerURIs() is used at connect time.
        MqttClient mqttClient = new MqttClient("tcp://localhost:1883", clientId, new MemoryPersistence());
        mqttClient.setCallback(this);
        mqttClient.connect(options);

        this.client = mqttClient;
        logger.info("MQTT connected to {} as {}", mqttInfo.endpointAddr, clientId);
    }

    /**
     * Subscribe to device telemetry and start realtime trigger.
     */
    public void subscribeAndTrigger(String appName, String productCode, String deviceSn) throws MqttException {
        // Store subscription for reconnection
        DeviceSubscription sub = new DeviceSubscription(appName, productCode, deviceSn);
        if (!subscriptions.contains(sub)) {
            subscriptions.add(sub);
        }

        MqttClient mqttClient = this.client;
        if (mqttClient == null || !mqttClient.isConnected()) {
            return;
        }

        subscribeDevice(mqttClient, appName, productCode, deviceSn);

        // Schedule periodic realtime triggers
        ScheduledFuture<?> job = scheduler.scheduleWithFixedDelay(
                () -> sendRealtimeTrigger(deviceSn, productCode),
                REALTIME_TRIGGER_INTERVAL_SECONDS,
                REALTIME_TRIGGER_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
        triggerJobs.add(job);
    }

    /**
     * Subscribe to a single device topic and send an initial realtime trigger.
     */
    private void subscribeDevice(MqttClient mqttClient, String appName, String productCode, String deviceSn)
            throws MqttException {
        String topic = "dt/" + appName + "/" + productCode + "/" + deviceSn + "/#";
        mqttClient.subscribe(topic, 0);
        logger.debug("Subscribed to MQTT topic: {}", topic);

        sendRealtimeTrigger(deviceSn, productCode);
    }

    public void disconnect() {
        for (ScheduledFuture<?> job : triggerJobs) {
            job.cancel(true);
        }
        triggerJobs.clear();
        subscriptions.clear();

        MqttClient mqttClient = this.client;
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) {
                    mqttClient.disconnect(5000);
                }
                mqttClient.close();
            } catch (MqttException e) {
                logger.debug("Error disconnecting MQTT: {}", e.getMessage());
            }
            this.client = null;
        }
    }

    public boolean isConnected() {
        MqttClient mqttClient = this.client;
        return mqttClient != null && mqttClient.isConnected();
    }

    // --- Command methods ---

    public void sendMinSocCommand(String deviceSn, String productCode, int socPercent) throws MqttException {
        byte[] payload = commandBuilder.buildMinSocCommand(socPercent);
        publishCommand(deviceSn, productCode, payload);
    }

    public void sendMaxLoadCommand(String deviceSn, String productCode, int watts) throws MqttException {
        byte[] payload = commandBuilder.buildMaxLoadCommand(watts);
        publishCommand(deviceSn, productCode, payload);
    }

    public void sendAcSocketCommand(String deviceSn, String productCode, boolean on) throws MqttException {
        byte[] payload = commandBuilder.buildAcSocketCommand(on);
        publishCommand(deviceSn, productCode, payload);
    }

    public void sendDisableGridExportCommand(String deviceSn, String productCode, boolean disable)
            throws MqttException {
        byte[] payload = commandBuilder.buildDisableGridExportCommand(disable);
        publishCommand(deviceSn, productCode, payload);
    }

    public void sendAcInputLimitCommand(String deviceSn, String productCode, int watts) throws MqttException {
        byte[] payload = commandBuilder.buildAcInputLimitCommand(watts);
        publishCommand(deviceSn, productCode, payload);
    }

    public void sendPvLimitCommand(String deviceSn, String productCode, int watts) throws MqttException {
        byte[] payload = commandBuilder.buildPvLimitCommand(watts);
        publishCommand(deviceSn, productCode, payload);
    }

    public void sendLightSwitchCommand(String deviceSn, String productCode, boolean on) throws MqttException {
        byte[] payload = commandBuilder.buildLightSwitchCommand(on);
        publishCommand(deviceSn, productCode, payload);
    }

    // --- MqttCallbackExtended ---

    @Override
    public void connectComplete(boolean reconnect, @Nullable String serverURI) {
        if (reconnect) {
            logger.info("MQTT reconnected to {}, re-subscribing {} device(s)", serverURI, subscriptions.size());
            MqttClient mqttClient = this.client;
            if (mqttClient == null) {
                return;
            }
            for (DeviceSubscription sub : subscriptions) {
                try {
                    subscribeDevice(mqttClient, sub.appName(), sub.productCode(), sub.deviceSn());
                } catch (MqttException e) {
                    logger.warn("Failed to re-subscribe device {} after reconnect: {}", sub.deviceSn(),
                            e.getMessage());
                }
            }
        }
    }

    @Override
    public void connectionLost(@Nullable Throwable cause) {
        logger.warn("MQTT connection lost (will auto-reconnect): {}",
                cause != null ? cause.getMessage() : "unknown");
    }

    @Override
    public void messageArrived(@Nullable String topic, @Nullable MqttMessage message) {
        if (topic == null || message == null) {
            return;
        }

        logger.trace("MQTT message on topic: {}, size: {} bytes", topic, message.getPayload().length);

        try {
            String jsonStr = new String(message.getPayload(), StandardCharsets.UTF_8);

            JsonObject json = gson.fromJson(jsonStr, JsonObject.class);
            if (json == null) {
                logger.debug("MQTT: failed to parse JSON");
                return;
            }

            // Extract device info from topic: dt/{app}/{product}/{sn}/
            String[] topicParts = topic.split("/");
            if (topicParts.length < 4) {
                logger.debug("MQTT: unexpected topic format: {}", topic);
                return;
            }
            String productCode = topicParts[2];
            String deviceSn = topicParts[3];

            // Extract payload -> data (base64 encoded binary)
            String payloadStr = json.has("payload") ? json.get("payload").getAsString() : "";
            if (payloadStr.isEmpty()) {
                logger.debug("MQTT: no payload field in message");
                return;
            }

            JsonObject payload = gson.fromJson(payloadStr, JsonObject.class);
            if (payload == null || !payload.has("data")) {
                logger.debug("MQTT: no data field in payload: {}", payloadStr);
                return;
            }

            String dataBase64 = payload.get("data").getAsString();
            byte[] rawData = Base64.getDecoder().decode(dataBase64);

            Map<String, Object> fields = parser.parse(rawData, productCode);
            if (!fields.isEmpty()) {
                listener.onDeviceUpdate(deviceSn, fields);
            }
        } catch (Exception e) {
            logger.debug("Error processing MQTT message: {}", e.getMessage(), e);
        }
    }

    @Override
    public void deliveryComplete(@Nullable IMqttDeliveryToken token) {
        // Not needed for QoS 0
    }

    // --- Private helpers ---

    private void sendRealtimeTrigger(String deviceSn, String productCode) {
        try {
            byte[] payload = commandBuilder.buildRealtimeTrigger(REALTIME_TRIGGER_TIMEOUT_SECONDS);
            publishCommand(deviceSn, productCode, payload);
            logger.debug("Sent realtime trigger for {} (timeout={}s)", deviceSn, REALTIME_TRIGGER_TIMEOUT_SECONDS);
        } catch (Exception e) {
            logger.debug("Failed to send realtime trigger: {}", e.getMessage());
        }
    }

    private void publishCommand(String deviceSn, String productCode, byte[] binaryPayload) throws MqttException {
        MqttClient mqttClient = this.client;
        String app = this.appName;
        if (mqttClient == null || !mqttClient.isConnected() || app == null) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }

        String topic = "cmd/" + app + "/" + productCode + "/" + deviceSn + "/req";

        // Wrap binary payload in JSON envelope
        String dataBase64 = Base64.getEncoder().encodeToString(binaryPayload);

        JsonObject payloadInner = new JsonObject();
        payloadInner.addProperty("account_id", "");
        payloadInner.addProperty("device_sn", deviceSn);
        payloadInner.addProperty("data", dataBase64);

        JsonObject head = new JsonObject();
        head.addProperty("version", "1.0.0.1");
        head.addProperty("client_id", mqttClient.getClientId());
        head.addProperty("sess_id", "");
        head.addProperty("msg_seq", 1);
        head.addProperty("seed", 1);
        head.addProperty("timestamp", System.currentTimeMillis() / 1000);
        head.addProperty("cmd_status", 2);
        head.addProperty("cmd", 17);
        head.addProperty("sign_code", 1);
        head.addProperty("device_pn", productCode);
        head.addProperty("device_sn", deviceSn);

        JsonObject envelope = new JsonObject();
        envelope.add("head", head);
        envelope.addProperty("payload", gson.toJson(payloadInner));

        MqttMessage msg = new MqttMessage(gson.toJson(envelope).getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        mqttClient.publish(topic, msg);
    }

    /**
     * Create an SSLSocketFactory from PEM certificate strings.
     */
    private SSLSocketFactory createSslSocketFactory(String caPem, String certPem, String keyPem) throws Exception {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

        // Root CA certificate -> TrustManager
        X509Certificate caCert = (X509Certificate) certFactory
                .generateCertificate(new ByteArrayInputStream(caPem.getBytes(StandardCharsets.UTF_8)));
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca", caCert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // Client certificate + private key -> KeyManager
        X509Certificate clientCert = (X509Certificate) certFactory
                .generateCertificate(new ByteArrayInputStream(certPem.getBytes(StandardCharsets.UTF_8)));
        PrivateKey privateKey = loadPrivateKey(keyPem);
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("client", privateKey, "".toCharArray(), new java.security.cert.Certificate[]{clientCert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "".toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext.getSocketFactory();
    }

    /**
     * Load a PKCS#8 private key from a PEM string.
     */
    private PrivateKey loadPrivateKey(String keyPem) throws Exception {
        String stripped = keyPem
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN EC PRIVATE KEY-----", "")
                .replace("-----END EC PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(stripped);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);

        // Try RSA first, then EC
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (Exception e) {
            return KeyFactory.getInstance("EC").generatePrivate(keySpec);
        }
    }
}
