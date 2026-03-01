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

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public final class ApiEndpoints {

    public static final String BASE_URL_EU = "https://ankerpower-api-eu.anker.com";
    public static final String BASE_URL_US = "https://ankerpower-api.anker.com";

    // Auth
    public static final String LOGIN = "passport/login";

    // Power service - site
    public static final String GET_SITE_LIST = "power_service/v1/site/get_site_list";
    public static final String GET_SITE_HOMEPAGE = "power_service/v1/site/get_site_homepage";
    public static final String GET_SCENE_INFO = "power_service/v1/site/get_scen_info";
    public static final String GET_DEVICE_PARAM = "power_service/v1/site/get_site_device_param";
    public static final String SET_DEVICE_PARAM = "power_service/v1/site/set_site_device_param";
    public static final String ENERGY_ANALYSIS = "power_service/v1/site/energy_analysis";

    // Power service - app/device
    public static final String BIND_DEVICES = "power_service/v1/app/get_relate_and_bind_devices";
    public static final String GET_DEVICE_ATTRS = "power_service/v1/app/device/get_device_attrs";
    public static final String SET_DEVICE_ATTRS = "power_service/v1/app/device/set_device_attrs";
    public static final String GET_POWER_CUTOFF = "power_service/v1/app/compatible/get_power_cutoff";
    public static final String SET_POWER_CUTOFF = "power_service/v1/app/compatible/set_power_cutoff";
    public static final String GET_MQTT_INFO = "app/devicemanage/get_user_mqtt_info";

    // HES service (Solarbank 3 Pro)
    public static final String HES_GET_SYSTEM_RUNNING_INFO = "charging_hes_svc/get_system_running_info";
    public static final String HES_GET_DEV_INFO = "charging_hes_svc/get_hes_dev_info";
    public static final String HES_ENERGY_STATISTICS = "charging_hes_svc/get_energy_statistics";

    private ApiEndpoints() {
    }

    public static String getBaseUrl(String server) {
        return "com".equalsIgnoreCase(server) ? BASE_URL_US : BASE_URL_EU;
    }
}
