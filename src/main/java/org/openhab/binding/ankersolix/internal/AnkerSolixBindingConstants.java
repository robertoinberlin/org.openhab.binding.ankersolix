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
package org.openhab.binding.ankersolix.internal;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

@NonNullByDefault
public class AnkerSolixBindingConstants {

    public static final String BINDING_ID = "ankersolix";

    // Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_ACCOUNT = new ThingTypeUID(BINDING_ID, "account");
    public static final ThingTypeUID THING_TYPE_SOLARBANK = new ThingTypeUID(BINDING_ID, "solarbank");

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_ACCOUNT, THING_TYPE_SOLARBANK);

    // Channel Group IDs
    public static final String GROUP_POWER = "power";
    public static final String GROUP_SOLAR = "solar";
    public static final String GROUP_BATTERY = "battery";
    public static final String GROUP_GRID = "grid";
    public static final String GROUP_ENERGY = "energy";
    public static final String GROUP_SETTINGS = "settings";
    public static final String GROUP_INFO = "info";

    // Power channel IDs
    public static final String CHANNEL_PV_POWER = "photovoltaic-power";
    public static final String CHANNEL_OUTPUT_POWER = "output-power";
    public static final String CHANNEL_AC_OUTPUT_POWER = "ac-output-power";
    public static final String CHANNEL_HOME_DEMAND = "home-demand";

    // Solar channel IDs
    public static final String CHANNEL_PV1_POWER = "pv1-power";
    public static final String CHANNEL_PV2_POWER = "pv2-power";
    public static final String CHANNEL_PV3_POWER = "pv3-power";
    public static final String CHANNEL_PV4_POWER = "pv4-power";

    // Battery channel IDs
    public static final String CHANNEL_BATTERY_POWER = "battery-power";
    public static final String CHANNEL_BATTERY_SOC = "battery-soc";
    public static final String CHANNEL_TEMPERATURE = "temperature";

    // Grid channel IDs
    public static final String CHANNEL_GRID_POWER = "grid-power";

    // Energy channel IDs
    public static final String CHANNEL_PV_YIELD = "pv-yield";
    public static final String CHANNEL_CHARGED_ENERGY = "charged-energy";
    public static final String CHANNEL_DISCHARGED_ENERGY = "discharged-energy";
    public static final String CHANNEL_DAILY_SOLAR = "daily-solar";
    public static final String CHANNEL_DAILY_CHARGE = "daily-charge";
    public static final String CHANNEL_DAILY_DISCHARGE = "daily-discharge";
    public static final String CHANNEL_DAILY_GRID_IMPORT = "daily-grid-import";
    public static final String CHANNEL_DAILY_GRID_EXPORT = "daily-grid-export";

    // Settings channel IDs
    public static final String CHANNEL_MIN_SOC = "min-soc";
    public static final String CHANNEL_MAX_LOAD = "max-load";
    public static final String CHANNEL_AC_SOCKET = "ac-socket-switch";
    public static final String CHANNEL_AC_INPUT_LIMIT = "ac-input-limit";
    public static final String CHANNEL_DISABLE_GRID_EXPORT = "disable-grid-export";
    public static final String CHANNEL_PV_LIMIT = "pv-limit";
    public static final String CHANNEL_LIGHT_SWITCH = "light-switch";

    // REST Control channel IDs (work without MQTT)
    public static final String GROUP_REST_CONTROL = "restControl";
    public static final String CHANNEL_REST_HOME_LOAD = "home-load";
    public static final String CHANNEL_REST_OUTPUT_LIMIT = "output-limit";
    public static final String CHANNEL_REST_PV_LIMIT = "pv-input-limit";
    public static final String CHANNEL_REST_AC_LIMIT = "ac-input-limit";
    public static final String CHANNEL_REST_GRID_EXPORT = "grid-export-switch";

    // Info channel IDs
    public static final String CHANNEL_FIRMWARE_VERSION = "firmware-version";
    public static final String CHANNEL_ONLINE_STATUS = "online-status";
    public static final String CHANNEL_LAST_UPDATE = "last-update";
    public static final String CHANNEL_MQTT_LAST_UPDATE = "mqtt-last-update";
}
