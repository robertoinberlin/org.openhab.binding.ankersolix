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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses binary hex data from MQTT telemetry messages.
 *
 * Message format:
 * - Header: FF 09 [len LE 2B] [pattern 3B] [msgtype 2B] [optional increment 1B]
 * - Fields: repeating [tag 1B] [length 1B] [type 1B if len>1] [value NB]
 * - Checksum: XOR of all preceding bytes (last byte)
 */
@NonNullByDefault
public class MqttMessageParser {

    private static final Logger logger = LoggerFactory.getLogger(MqttMessageParser.class);

    // Field type codes
    private static final int TYPE_STR = 0x00;
    private static final int TYPE_UI = 0x01;
    private static final int TYPE_SILE = 0x02;
    private static final int TYPE_VAR = 0x03;
    private static final int TYPE_BIN = 0x04;
    private static final int TYPE_SFLE = 0x05;

    /**
     * Parse a raw MQTT binary payload into a map of field name -> value.
     *
     * @param rawData the raw binary data (after base64 decoding)
     * @param productCode the device product code (e.g., "A17C5")
     * @return map of field name to parsed value
     */
    public Map<String, Object> parse(byte[] rawData, String productCode) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (rawData.length < 9) {
            logger.warn("MQTT message too short: {} bytes", rawData.length);
            return result;
        }

        // Parse header
        if ((rawData[0] & 0xFF) != 0xFF || (rawData[1] & 0xFF) != 0x09) {
            logger.debug("Invalid MQTT message prefix: {:02X} {:02X}", rawData[0], rawData[1]);
            return result;
        }

        int msgLength = (rawData[2] & 0xFF) | ((rawData[3] & 0xFF) << 8); // little-endian
        int messageType = ((rawData[7] & 0xFF) << 8) | (rawData[8] & 0xFF); // big-endian for type

        logger.trace("MQTT message type=0x{}, length={}", String.format("%04X", messageType), msgLength);

        // Get field definitions for this product/message type
        Map<Integer, SolarbankFieldMap.FieldDef> fieldMap = SolarbankFieldMap.getFieldMap(productCode, messageType);
        if (fieldMap.isEmpty()) {
            logger.debug("No field map for product={} messageType=0x{}", productCode,
                    String.format("%04X", messageType));
            return result;
        }

        // Determine where fields start (after header, skip optional increment byte)
        int offset = 9;
        if (offset < rawData.length) {
            int possibleTag = rawData[offset] & 0xFF;
            // If byte 9 is NOT a field tag (0xA0-0xFE range), it's an increment byte
            if (possibleTag < 0xA0) {
                offset = 10;
            }
        }

        // Parse fields until we hit the last byte (checksum)
        int endOffset = rawData.length - 1; // last byte is XOR checksum
        while (offset < endOffset) {
            if (offset + 2 > endOffset) {
                break;
            }

            int tag = rawData[offset] & 0xFF;
            int fieldLength = rawData[offset + 1] & 0xFF;

            if (fieldLength == 0 || offset + 2 + fieldLength > rawData.length) {
                logger.trace("Invalid field length at offset {}: tag=0x{}, len={}", offset,
                        String.format("%02X", tag), fieldLength);
                break;
            }

            SolarbankFieldMap.FieldDef fieldDef = fieldMap.get(tag);

            if (fieldLength == 1) {
                // Single byte value, no type byte
                int value = rawData[offset + 2] & 0xFF;
                if (fieldDef != null) {
                    if (fieldDef.factor != 1.0) {
                        result.put(fieldDef.name, value * fieldDef.factor);
                    } else {
                        result.put(fieldDef.name, value);
                    }
                }
                offset += 3; // tag + length + 1 byte value
            } else {
                // Multi-byte: first data byte is type, remaining bytes are value
                int fieldType = rawData[offset + 2] & 0xFF;
                byte[] valueBytes = new byte[fieldLength - 1];
                System.arraycopy(rawData, offset + 3, valueBytes, 0, fieldLength - 1);

                if (fieldDef != null) {
                    parseFieldValue(result, fieldDef, fieldType, valueBytes);
                }
                offset += 2 + fieldLength; // tag + length + data
            }
        }

        return result;
    }

    private void parseFieldValue(Map<String, Object> result, SolarbankFieldMap.FieldDef fieldDef,
            int fieldType, byte[] valueBytes) {
        try {
            switch (fieldType) {
                case TYPE_STR:
                    String strValue = new String(valueBytes, StandardCharsets.UTF_8).trim();
                    result.put(fieldDef.name, strValue);
                    break;

                case TYPE_UI:
                    // Unsigned int, 1 byte big-endian
                    if (valueBytes.length >= 1) {
                        int uiValue = valueBytes[0] & 0xFF;
                        if (fieldDef.signed && uiValue > 127) {
                            uiValue -= 256;
                        }
                        double uiResult = uiValue * fieldDef.factor;
                        result.put(fieldDef.name, uiResult);
                    }
                    break;

                case TYPE_SILE:
                    // Signed int, 2 bytes little-endian
                    if (valueBytes.length >= 2) {
                        ByteBuffer bb = ByteBuffer.wrap(valueBytes).order(ByteOrder.LITTLE_ENDIAN);
                        int sileValue = fieldDef.signed ? bb.getShort() : (bb.getShort() & 0xFFFF);
                        double sileResult = sileValue * fieldDef.factor;
                        result.put(fieldDef.name, sileResult);
                    }
                    break;

                case TYPE_VAR:
                    // Variable length int, little-endian (typically 4 bytes)
                    if (valueBytes.length >= 4) {
                        ByteBuffer bb = ByteBuffer.wrap(valueBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN);
                        long varValue = fieldDef.signed ? bb.getInt() : (bb.getInt() & 0xFFFFFFFFL);
                        double varResult = varValue * fieldDef.factor;
                        result.put(fieldDef.name, varResult);
                    } else if (valueBytes.length >= 2) {
                        ByteBuffer bb = ByteBuffer.wrap(valueBytes, 0, 2).order(ByteOrder.LITTLE_ENDIAN);
                        int varValue = fieldDef.signed ? bb.getShort() : (bb.getShort() & 0xFFFF);
                        result.put(fieldDef.name, varValue * fieldDef.factor);
                    } else if (valueBytes.length == 1) {
                        result.put(fieldDef.name, (valueBytes[0] & 0xFF) * fieldDef.factor);
                    }
                    break;

                case TYPE_BIN:
                    // Bitmap field - extract individual bits
                    Map<String, Integer> bitmasks = fieldDef.bitmasks;
                    if (bitmasks != null && valueBytes.length >= 1) {
                        int byteVal = valueBytes[0] & 0xFF;
                        for (Map.Entry<String, Integer> entry : bitmasks.entrySet()) {
                            int mask = entry.getValue();
                            // Find shift amount (position of lowest set bit)
                            int shift = Integer.numberOfTrailingZeros(mask);
                            int bitValue = (byteVal & mask) >> shift;
                            result.put(entry.getKey(), bitValue);
                        }
                    }
                    break;

                case TYPE_SFLE:
                    // Signed float, 4 bytes little-endian IEEE 754
                    if (valueBytes.length >= 4) {
                        ByteBuffer bb = ByteBuffer.wrap(valueBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN);
                        float floatValue = bb.getFloat();
                        result.put(fieldDef.name, (double) (floatValue * fieldDef.factor));
                    }
                    break;

                default:
                    logger.trace("Unknown field type 0x{} for field {}", String.format("%02X", fieldType),
                            fieldDef.name);
            }
        } catch (Exception e) {
            logger.debug("Error parsing field {}: {}", fieldDef.name, e.getMessage());
        }
    }
}
