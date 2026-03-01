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

import com.google.gson.annotations.SerializedName;

public class SiteListResponse {

    @SerializedName("site_list")
    public List<SiteInfo> siteList = new ArrayList<>();

    public static class SiteInfo {
        @SerializedName("site_id")
        public String siteId = "";

        @SerializedName("site_name")
        public String siteName = "";

        @SerializedName("site_type")
        public int siteType;
    }
}
