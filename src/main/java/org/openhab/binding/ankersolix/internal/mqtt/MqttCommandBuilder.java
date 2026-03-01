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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Builds binary command payloads for Solarbank 3 Pro MQTT commands.
 *
 * Command format:
 * FF 09 [length LE 2B] 03 00 0F [msgtype 2B] [fields...] [XOR checksum 1B]
 *
 * Common fields in every command:
 * - a1 01 22 (pattern marker)
 * - fe 05 03 [unix timestamp LE 4B] (message timestamp)
 */
@NonNullByDefault
public class MqttCommandBuilder {

    /**
     * Build realtime_trigger command (msg type 0x0057).
     * Enables high-frequency (3-5s) telemetry updates for a timeout period.
     */
    public byte[] buildRealtimeTrigger(int timeoutSeconds) {
        ByteArrayOutputStream fields = new ByteArrayOutputStream();

        // a1: pattern_22
        writePatternField(fields);
        // a2: enable flag (ui, 1=on)
        writeUiField(fields, 0xA2, 1);
        // a3: timeout in seconds (var, 4 bytes LE)
        writeVarField(fields, 0xA3, timeoutSeconds);
        // fe: timestamp
        writeTimestampField(fields);

        return buildMessage(0x00, 0x57, fields.toByteArray());
    }

    /**
     * Build min SOC command (msg type 0x0067).
     */
    public byte[] buildMinSocCommand(int socPercent) {
        ByteArrayOutputStream fields = new ByteArrayOutputStream();

        writePatternField(fields);
        writeUiField(fields, 0xA2, socPercent);
        writeTimestampField(fields);

        return buildMessage(0x00, 0x67, fields.toByteArray());
    }

    /**
     * Build max load command (msg type 0x0080).
     */
    public byte[] buildMaxLoadCommand(int watts) {
        ByteArrayOutputStream fields = new ByteArrayOutputStream();

        writePatternField(fields);
        writeSileField(fields, 0xA2, watts);
        writeSileField(fields, 0xA3, 0); // type: 0=individual
        writeTimestampField(fields);

        return buildMessage(0x00, 0x80, fields.toByteArray());
    }

    /**
     * Build AC socket switch command (msg type 0x0073).
     */
    public byte[] buildAcSocketCommand(boolean on) {
        ByteArrayOutputStream fields = new ByteArrayOutputStream();

        writePatternField(fields);
        writeUiField(fields, 0xA2, on ? 1 : 0);
        writeTimestampField(fields);

        return buildMessage(0x00, 0x73, fields.toByteArray());
    }

    /**
     * Build disable grid export command (msg type 0x0080).
     */
    public byte[] buildDisableGridExportCommand(boolean disable) {
        ByteArrayOutputStream fields = new ByteArrayOutputStream();

        writePatternField(fields);
        writeSileField(fields, 0xA5, 0);              // unknown, default 0
        writeSileField(fields, 0xA6, disable ? 1 : 0); // disable switch
        writeSileField(fields, 0xA9, 0);              // grid export limit, default 0
        writeTimestampField(fields);

        return buildMessage(0x00, 0x80, fields.toByteArray());
    }

    /**
     * Build AC input limit command (msg type 0x0080).
     */
    public byte[] buildAcInputLimitCommand(int watts) {
        ByteArrayOutputStream fields = new ByteArrayOutputStream();

        writePatternField(fields);
        writeSileField(fields, 0xA8, watts);
        writeTimestampField(fields);

        return buildMessage(0x00, 0x80, fields.toByteArray());
    }

    /**
     * Build PV limit command (msg type 0x0080).
     */
    public byte[] buildPvLimitCommand(int watts) {
        ByteArrayOutputStream fields = new ByteArrayOutputStream();

        writePatternField(fields);
        writeSileField(fields, 0xA7, watts);
        writeTimestampField(fields);

        return buildMessage(0x00, 0x80, fields.toByteArray());
    }

    /**
     * Build light switch command (msg type 0x0068).
     */
    public byte[] buildLightSwitchCommand(boolean on) {
        ByteArrayOutputStream fields = new ByteArrayOutputStream();

        writePatternField(fields);
        writeUiField(fields, 0xA2, 0);      // light_mode, default 0
        writeUiField(fields, 0xA3, on ? 1 : 0); // light_off_switch
        writeTimestampField(fields);

        return buildMessage(0x00, 0x68, fields.toByteArray());
    }

    // --- Message assembly ---

    private byte[] buildMessage(int msgTypeByte1, int msgTypeByte2, byte[] fieldData) {
        // Header: FF 09 [len LE 2B] 03 00 0F [msgtype 2B]
        int totalLength = 9 + fieldData.length + 1; // header(9) + fields + checksum(1)
        // Length field counts from itself (offset 2) to end including checksum
        int lenFieldValue = totalLength;

        ByteArrayOutputStream msg = new ByteArrayOutputStream();
        msg.write(0xFF);
        msg.write(0x09);
        msg.write(lenFieldValue & 0xFF);         // length low byte
        msg.write((lenFieldValue >> 8) & 0xFF);  // length high byte
        msg.write(0x03);
        msg.write(0x00);
        msg.write(0x0F);
        msg.write(msgTypeByte1);
        msg.write(msgTypeByte2);

        // Fields
        msg.write(fieldData, 0, fieldData.length);

        // XOR checksum
        byte[] bytes = msg.toByteArray();
        int checksum = 0;
        for (byte b : bytes) {
            checksum ^= (b & 0xFF);
        }
        msg.write(checksum & 0xFF);

        return msg.toByteArray();
    }

    // --- Field writing helpers ---

    /** Write pattern field: a1 01 22 */
    private void writePatternField(ByteArrayOutputStream out) {
        out.write(0xA1);
        out.write(0x01);
        out.write(0x22);
    }

    /** Write unsigned int field: [tag] 02 01 [value] */
    private void writeUiField(ByteArrayOutputStream out, int tag, int value) {
        out.write(tag);
        out.write(0x02); // length = 2 (type byte + 1 byte value)
        out.write(0x01); // type = ui
        out.write(value & 0xFF);
    }

    /** Write signed int little-endian field: [tag] 03 02 [value LE 2B] */
    private void writeSileField(ByteArrayOutputStream out, int tag, int value) {
        out.write(tag);
        out.write(0x03); // length = 3 (type byte + 2 byte value)
        out.write(0x02); // type = sile
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    /** Write variable length int field: [tag] 05 03 [value LE 4B] */
    private void writeVarField(ByteArrayOutputStream out, int tag, int value) {
        out.write(tag);
        out.write(0x05); // length = 5 (type byte + 4 byte value)
        out.write(0x03); // type = var
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value);
        byte[] bytes = bb.array();
        out.write(bytes, 0, 4);
    }

    /** Write timestamp field: fe 05 03 [unix seconds LE 4B] */
    private void writeTimestampField(ByteArrayOutputStream out) {
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        writeVarField(out, 0xFE, timestamp);
    }
}
