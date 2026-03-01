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
package org.openhab.binding.ankersolix.internal.api;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.ankersolix.internal.api.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

@NonNullByDefault
public class AnkerSolixApiClient {

    private static final Logger logger = LoggerFactory.getLogger(AnkerSolixApiClient.class);
    private static final int TIMEOUT_SECONDS = 15;

    private final HttpClient httpClient;
    private final AnkerSolixAuth auth;
    private final RateLimiter rateLimiter;
    private final Gson gson;
    private final String baseUrl;
    private final String country;
    private final String email;
    private final String password;

    private boolean authenticated = false;

    public AnkerSolixApiClient(HttpClient httpClient, AnkerSolixAuth auth, String server, String country,
            String email, String password) {
        this.httpClient = httpClient;
        this.auth = auth;
        this.rateLimiter = new RateLimiter();
        this.gson = new Gson();
        this.baseUrl = ApiEndpoints.getBaseUrl(server);
        this.country = country;
        this.email = email;
        this.password = password;
    }

    /**
     * Authenticate with the Anker cloud API.
     */
    public LoginResponse authenticate() throws Exception {
        logger.debug("Authenticating with Anker cloud API at {}", baseUrl);

        String encryptedPassword = auth.encryptPassword(password);

        JsonObject body = new JsonObject();
        body.addProperty("ab", country.toUpperCase());
        body.addProperty("email", email);
        body.addProperty("password", encryptedPassword);
        body.addProperty("enc", 0);
        body.addProperty("time_zone", AnkerSolixAuth.getTimezoneOffsetMs());
        body.addProperty("transaction", String.valueOf(System.currentTimeMillis()));

        JsonObject clientSecret = new JsonObject();
        clientSecret.addProperty("public_key", auth.getClientPublicKeyHex());
        body.add("client_secret_info", clientSecret);

        ApiResponse<LoginResponse> response = postRequest(ApiEndpoints.LOGIN, body,
                new TypeToken<ApiResponse<LoginResponse>>() {
                }.getType(), false);

        if (!response.isSuccess()) {
            throw new ApiException("Login failed: code=" + response.code + " msg=" + response.msg);
        }

        LoginResponse login = response.data;
        auth.setTokens(login.authToken, login.userId, login.tokenExpiresAt);
        authenticated = true;
        logger.info("Successfully authenticated as {}", login.nickName);
        return login;
    }

    /**
     * Get list of sites.
     */
    public SiteListResponse getSiteList() throws Exception {
        JsonObject body = new JsonObject();
        return postAuthenticated(ApiEndpoints.GET_SITE_LIST, body,
                new TypeToken<ApiResponse<SiteListResponse>>() {
                }.getType());
    }

    /**
     * Get site homepage data (primary polling endpoint).
     */
    public SiteHomepageResponse getSiteHomepage(String siteId) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("site_id", siteId);
        return postAuthenticated(ApiEndpoints.GET_SITE_HOMEPAGE, body,
                new TypeToken<ApiResponse<SiteHomepageResponse>>() {
                }.getType());
    }

    /**
     * Get MQTT connection info (certificates and broker endpoint).
     */
    public MqttInfoResponse getMqttInfo() throws Exception {
        JsonObject body = new JsonObject();
        return postAuthenticated(ApiEndpoints.GET_MQTT_INFO, body,
                new TypeToken<ApiResponse<MqttInfoResponse>>() {
                }.getType());
    }

    /**
     * Get device parameters (schedules, settings).
     *
     * @param paramType 6=SB2/3 schedule, 12=tariff schedule, 18=station settings
     */
    public JsonObject getDeviceParam(String siteId, int paramType) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("site_id", siteId);
        body.addProperty("param_type", String.valueOf(paramType));
        return postAuthenticated(ApiEndpoints.GET_DEVICE_PARAM, body,
                new TypeToken<ApiResponse<JsonObject>>() {
                }.getType());
    }

    /**
     * Set power cutoff (minimum SOC) via REST.
     */
    public void setPowerCutoff(String siteId, String deviceSn, int cutoffDataId) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("site_id", siteId);
        body.addProperty("device_sn", deviceSn);
        body.addProperty("cutoff_data_id", cutoffDataId);
        postAuthenticated(ApiEndpoints.SET_POWER_CUTOFF, body,
                new TypeToken<ApiResponse<JsonObject>>() {
                }.getType());
    }

    /**
     * Get HES system running info.
     */
    public JsonObject getSystemRunningInfo(String siteId) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("siteId", siteId);
        return postAuthenticated(ApiEndpoints.HES_GET_SYSTEM_RUNNING_INFO, body,
                new TypeToken<ApiResponse<JsonObject>>() {
                }.getType());
    }

    /**
     * Set device attributes via REST (power limits, grid export switch).
     *
     * @param deviceSn device serial number
     * @param attributes map of attribute key to value (e.g., "power_limit" -> 800)
     */
    public void setDeviceAttributes(String deviceSn, Map<String, Object> attributes) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("device_sn", deviceSn);
        JsonObject attrs = new JsonObject();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getValue() instanceof Number number) {
                attrs.addProperty(entry.getKey(), number);
            } else if (entry.getValue() instanceof Boolean bool) {
                attrs.addProperty(entry.getKey(), bool);
            } else {
                attrs.addProperty(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        body.add("attributes", attrs);
        postAuthenticated(ApiEndpoints.SET_DEVICE_ATTRS, body,
                new TypeToken<ApiResponse<JsonObject>>() {
                }.getType());
        logger.debug("Set device attributes for {}: {}", deviceSn, attrs);
    }

    /**
     * Get device attributes via REST.
     */
    public JsonObject getDeviceAttributes(String deviceSn, String... attributeNames) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("device_sn", deviceSn);
        com.google.gson.JsonArray attrsArray = new com.google.gson.JsonArray();
        for (String attr : attributeNames) {
            attrsArray.add(attr);
        }
        body.add("attributes", attrsArray);
        return postAuthenticated(ApiEndpoints.GET_DEVICE_ATTRS, body,
                new TypeToken<ApiResponse<JsonObject>>() {
                }.getType());
    }

    /**
     * Set home load via SB2/SB3 schedule (param_type=6).
     * Sets a single all-day schedule with the given power output.
     *
     * @param siteId site ID
     * @param watts home load power in watts
     */
    public void setHomeLoad(String siteId, int watts) throws Exception {
        // Build compact param_data JSON for a simple all-day schedule
        JsonObject range = new JsonObject();
        range.addProperty("start_time", "00:00");
        range.addProperty("end_time", "24:00");
        range.addProperty("power", watts);

        com.google.gson.JsonArray ranges = new com.google.gson.JsonArray();
        ranges.add(range);

        com.google.gson.JsonArray week = new com.google.gson.JsonArray();
        for (int i = 0; i <= 6; i++) {
            week.add(i);
        }

        JsonObject plan = new JsonObject();
        plan.addProperty("index", 0);
        plan.add("week", week);
        plan.add("ranges", ranges);

        com.google.gson.JsonArray customRatePlan = new com.google.gson.JsonArray();
        customRatePlan.add(plan);

        JsonObject paramData = new JsonObject();
        paramData.addProperty("mode_type", 3); // manual mode
        paramData.add("custom_rate_plan", customRatePlan);
        paramData.addProperty("default_home_load", watts);
        paramData.addProperty("max_load", 800);
        paramData.addProperty("min_load", 0);
        paramData.addProperty("step", 10);

        // Serialize param_data as compact JSON string
        String paramDataStr = gson.toJson(paramData);

        JsonObject body = new JsonObject();
        body.addProperty("site_id", siteId);
        body.addProperty("param_type", "6");
        body.addProperty("cmd", 17);
        body.addProperty("param_data", paramDataStr);

        postAuthenticated(ApiEndpoints.SET_DEVICE_PARAM, body,
                new TypeToken<ApiResponse<JsonObject>>() {
                }.getType());
        logger.debug("Set home load for site {} to {} W", siteId, watts);
    }

    public boolean isAuthenticated() {
        return authenticated && auth.hasToken() && !auth.isTokenExpired();
    }

    // --- Private helpers ---

    private <T> T postAuthenticated(String endpoint, JsonObject body, Type responseType) throws Exception {
        ensureAuthenticated();
        ApiResponse<T> response = postRequest(endpoint, body, responseType, true);

        if (response.code == 26084) {
            // Token kicked out - re-authenticate
            logger.warn("Token kicked out, re-authenticating...");
            authenticate();
            response = postRequest(endpoint, body, responseType, true);
        }

        if (!response.isSuccess()) {
            throw new ApiException("API error: code=" + response.code + " msg=" + response.msg);
        }

        return response.data;
    }

    private void ensureAuthenticated() throws Exception {
        if (!authenticated || auth.isTokenExpired()) {
            authenticate();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ApiResponse<T> postRequest(String endpoint, JsonObject body, Type responseType,
            boolean withAuth) throws Exception {
        rateLimiter.acquire();

        String url = baseUrl + "/" + endpoint;
        String jsonBody = gson.toJson(body);

        logger.trace("POST {} body={}", endpoint, jsonBody);

        Request request = httpClient.newRequest(url)
                .method(HttpMethod.POST)
                .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .content(new StringContentProvider("application/json", jsonBody, StandardCharsets.UTF_8));

        Map<String, String> headers = auth.getAuthHeaders(country);
        if (!withAuth) {
            // For login, still send base headers but not auth tokens
            headers.remove("x-auth-token");
            headers.remove("gtoken");
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            request.header(entry.getKey(), entry.getValue());
        }

        ContentResponse httpResponse = request.send();
        int statusCode = httpResponse.getStatus();
        String responseBody = httpResponse.getContentAsString();

        logger.trace("Response {} status={} body={}", endpoint, statusCode, responseBody);

        if (statusCode == 429) {
            logger.warn("Rate limited (429), waiting 65 seconds...");
            Thread.sleep(65_000);
            httpResponse = request.send();
            responseBody = httpResponse.getContentAsString();
        } else if (statusCode == 502 || statusCode == 504) {
            logger.warn("Server error ({}), retrying in 3 seconds...", statusCode);
            Thread.sleep(3_000);
            httpResponse = request.send();
            responseBody = httpResponse.getContentAsString();
        }

        if (statusCode == 401 || statusCode == 403) {
            throw new AuthException("Authentication error: " + statusCode);
        }

        return gson.fromJson(responseBody, responseType);
    }

    public static class ApiException extends Exception {
        private static final long serialVersionUID = 1L;

        public ApiException(String message) {
            super(message);
        }
    }

    public static class AuthException extends Exception {
        private static final long serialVersionUID = 1L;

        public AuthException(String message) {
            super(message);
        }
    }
}
