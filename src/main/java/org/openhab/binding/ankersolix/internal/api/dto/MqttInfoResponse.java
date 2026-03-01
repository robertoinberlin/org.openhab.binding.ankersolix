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

import com.google.gson.annotations.SerializedName;

public class MqttInfoResponse {

    @SerializedName("app_name")
    public String appName = "";

    @SerializedName("thing_name")
    public String thingName = "";

    @SerializedName("endpoint_addr")
    public String endpointAddr = "";

    @SerializedName("certificate_pem")
    public String certificatePem = "";

    @SerializedName("private_key")
    public String privateKey = "";

    @SerializedName("aws_root_ca1_pem")
    public String awsRootCa1Pem = "";

    @SerializedName("certificate_id")
    public String certificateId = "";
}
