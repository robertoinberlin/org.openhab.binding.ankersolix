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

public class LoginResponse {

    @SerializedName("user_id")
    public String userId = "";

    @SerializedName("email")
    public String email = "";

    @SerializedName("nick_name")
    public String nickName = "";

    @SerializedName("auth_token")
    public String authToken = "";

    @SerializedName("token_expires_at")
    public long tokenExpiresAt;

    @SerializedName("country_code")
    public String countryCode = "";
}
