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

/**
 * Generic API response wrapper. All Anker API responses follow this structure.
 */
public class ApiResponse<T> {

    @SerializedName("code")
    public int code;

    @SerializedName("msg")
    public String msg = "";

    @SerializedName("data")
    public T data;

    public boolean isSuccess() {
        return code == 0;
    }
}
