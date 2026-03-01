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
package org.openhab.binding.ankersolix.internal.api.dto;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/**
 * Response from get_site_homepage endpoint.
 * This is the primary polling endpoint providing power overview data.
 */
public class SiteHomepageResponse {

    @SerializedName("site_id")
    public String siteId = "";

    @SerializedName("home_info")
    public HomeInfo homeInfo = new HomeInfo();

    @SerializedName("energy_info")
    public EnergyInfo energyInfo = new EnergyInfo();

    @SerializedName("solarbank_list")
    public List<SolarbankInfo> solarbankList = new ArrayList<>();

    @SerializedName("solarbank_info")
    public SolarbankSummary solarbankInfo = new SolarbankSummary();

    public static class HomeInfo {
        @SerializedName("solar_power")
        public String solarPower = "0";

        @SerializedName("solar_power_unit")
        public String solarPowerUnit = "W";

        @SerializedName("battery_power")
        public String batteryPower = "0";

        @SerializedName("battery_power_unit")
        public String batteryPowerUnit = "W";

        @SerializedName("grid_power")
        public String gridPower = "0";

        @SerializedName("home_usage_power")
        public String homeUsagePower = "0";

        @SerializedName("photovoltaic_to_grid_power")
        public String pvToGridPower = "0";

        @SerializedName("battery_to_home_power")
        public String batteryToHomePower = "0";

        @SerializedName("grid_to_home_power")
        public String gridToHomePower = "0";
    }

    public static class EnergyInfo {
        @SerializedName("solar_production")
        public String solarProduction = "0";

        @SerializedName("solar_production_unit")
        public String solarProductionUnit = "kWh";

        @SerializedName("battery_discharge")
        public String batteryDischarge = "0";

        @SerializedName("battery_charge")
        public String batteryCharge = "0";

        @SerializedName("grid_import")
        public String gridImport = "0";

        @SerializedName("grid_export")
        public String gridExport = "0";
    }

    public static class SolarbankInfo {
        @SerializedName("device_sn")
        public String deviceSn = "";

        @SerializedName("device_name")
        public String deviceName = "";

        @SerializedName("battery_power")
        public String batteryPower = "0";

        @SerializedName("battery_soc")
        public String batterySoc = "0";

        @SerializedName("photovoltaic_power")
        public String pvPower = "0";

        @SerializedName("output_power")
        public String outputPower = "0";

        @SerializedName("charging_power")
        public String chargingPower = "0";

        @SerializedName("status")
        public int status;

        @SerializedName("sw_version")
        public String swVersion = "";

        @SerializedName("wifi_online")
        public boolean wifiOnline;
    }

    public static class SolarbankSummary {
        @SerializedName("total_charging_power")
        public String totalChargingPower = "0";

        @SerializedName("total_output_power")
        public String totalOutputPower = "0";

        @SerializedName("total_battery_power")
        public String totalBatteryPower = "0";

        @SerializedName("total_photovoltaic_power")
        public String totalPvPower = "0";
    }
}
