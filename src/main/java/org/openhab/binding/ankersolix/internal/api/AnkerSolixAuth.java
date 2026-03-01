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

import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles ECDH P-256 key exchange and AES-256-CBC password encryption
 * for Anker Solix cloud API authentication.
 */
@NonNullByDefault
public class AnkerSolixAuth {

    private static final Logger logger = LoggerFactory.getLogger(AnkerSolixAuth.class);

    private static final String SERVER_PUBLIC_KEY_HEX = "04c5c00c4f8d1197cc7c3167c52bf7acb054d722f0ef08dcd7e0883236e0d72a3868d9750cb47fa4619248f3d83f0f662671dadc6e2d31c2f41db0161651c7c076";

    private final KeyPair ecKeyPair;
    private final byte[] sharedSecret;

    private @Nullable String authToken;
    private @Nullable String userId;
    private @Nullable String gtoken;
    private long tokenExpiresAt;

    public AnkerSolixAuth() throws Exception {
        // Generate ECDH P-256 key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"));
        this.ecKeyPair = keyGen.generateKeyPair();

        // Derive shared secret with the server's hardcoded public key
        ECPublicKey serverPubKey = decodeUncompressedPoint(HexFormat.of().parseHex(SERVER_PUBLIC_KEY_HEX));
        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
        keyAgreement.init(ecKeyPair.getPrivate());
        keyAgreement.doPhase(serverPubKey, true);
        this.sharedSecret = keyAgreement.generateSecret();

        logger.debug("ECDH key exchange completed, shared secret length: {} bytes", sharedSecret.length);
    }

    /**
     * Encrypt a password using AES-256-CBC with the ECDH shared secret.
     * Key = shared secret (32 bytes), IV = first 16 bytes of shared secret, PKCS5 padding.
     */
    public String encryptPassword(String password) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(sharedSecret, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(sharedSecret, 0, 16);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * Get the client's public key as an uncompressed hex string (04 + X + Y).
     */
    public String getClientPublicKeyHex() {
        ECPublicKey pubKey = (ECPublicKey) ecKeyPair.getPublic();
        ECPoint point = pubKey.getW();
        byte[] x = toUnsignedBytes(point.getAffineX().toByteArray(), 32);
        byte[] y = toUnsignedBytes(point.getAffineY().toByteArray(), 32);

        byte[] uncompressed = new byte[65];
        uncompressed[0] = 0x04;
        System.arraycopy(x, 0, uncompressed, 1, 32);
        System.arraycopy(y, 0, uncompressed, 33, 32);
        return HexFormat.of().formatHex(uncompressed);
    }

    /**
     * Store authentication tokens from login response.
     */
    public void setTokens(String authToken, String userId, long expiresAt) throws Exception {
        this.authToken = authToken;
        this.userId = userId;
        this.tokenExpiresAt = expiresAt;
        this.gtoken = md5Hex(userId);
        logger.debug("Auth tokens set, expires at: {}", Instant.ofEpochSecond(expiresAt));
    }

    public boolean isTokenExpired() {
        return Instant.now().getEpochSecond() >= tokenExpiresAt - 60;
    }

    public boolean hasToken() {
        return authToken != null;
    }

    /**
     * Generate HTTP headers for authenticated API requests.
     */
    public Map<String, String> getAuthHeaders(String country) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Model-Type", "DESKTOP");
        headers.put("App-Name", "anker_power");
        headers.put("Os-Type", "android");
        headers.put("Country", country.toUpperCase());
        headers.put("Timezone", getTimezoneGMTString());

        String token = this.authToken;
        String gt = this.gtoken;
        if (token != null && gt != null) {
            headers.put("x-auth-token", token);
            headers.put("gtoken", gt);
        }

        return headers;
    }

    public @Nullable String getUserId() {
        return userId;
    }

    // --- Private helpers ---

    /**
     * Decode an uncompressed EC point (0x04 + 32-byte X + 32-byte Y) into an ECPublicKey.
     */
    private static ECPublicKey decodeUncompressedPoint(byte[] encoded) throws Exception {
        if (encoded.length != 65 || encoded[0] != 0x04) {
            throw new IllegalArgumentException("Expected 65-byte uncompressed EC point starting with 0x04");
        }

        byte[] xBytes = new byte[32];
        byte[] yBytes = new byte[32];
        System.arraycopy(encoded, 1, xBytes, 0, 32);
        System.arraycopy(encoded, 33, yBytes, 0, 32);

        java.math.BigInteger x = new java.math.BigInteger(1, xBytes);
        java.math.BigInteger y = new java.math.BigInteger(1, yBytes);
        ECPoint point = new ECPoint(x, y);

        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);

        ECPublicKeySpec pubSpec = new ECPublicKeySpec(point, ecSpec);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return (ECPublicKey) keyFactory.generatePublic(pubSpec);
    }

    /**
     * Compute MD5 hex digest of a string.
     */
    private static String md5Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    /**
     * Get timezone string in "GMT+HH:MM" format.
     */
    private static String getTimezoneGMTString() {
        ZonedDateTime now = ZonedDateTime.now();
        int offsetSeconds = now.getOffset().getTotalSeconds();
        int hours = offsetSeconds / 3600;
        int minutes = Math.abs((offsetSeconds % 3600) / 60);
        return String.format("GMT%+03d:%02d", hours, minutes);
    }

    /**
     * Get timezone offset in milliseconds.
     */
    public static long getTimezoneOffsetMs() {
        ZonedDateTime now = ZonedDateTime.now();
        return now.getOffset().getTotalSeconds() * 1000L;
    }

    /**
     * Pad or trim BigInteger byte array to exactly 'length' bytes (unsigned, big-endian).
     */
    private static byte[] toUnsignedBytes(byte[] bigIntBytes, int length) {
        if (bigIntBytes.length == length) {
            return bigIntBytes;
        }
        byte[] result = new byte[length];
        if (bigIntBytes.length > length) {
            // BigInteger may prepend a 0x00 sign byte
            System.arraycopy(bigIntBytes, bigIntBytes.length - length, result, 0, length);
        } else {
            // Pad with leading zeros
            System.arraycopy(bigIntBytes, 0, result, length - bigIntBytes.length, bigIntBytes.length);
        }
        return result;
    }
}
