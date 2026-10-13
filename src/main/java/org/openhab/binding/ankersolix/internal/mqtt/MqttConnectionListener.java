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

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public interface MqttConnectionListener {

    void onMqttConnectionStateChanged(boolean connected);

    /** The MQTT client detected a dead or stale connection and needs fresh credentials. */
    void onMqttReconnectNeeded();
}
