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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Simple sliding-window rate limiter for API calls.
 * Enforces max 5 requests per 60 seconds with 300ms minimum gap.
 */
@NonNullByDefault
public class RateLimiter {

    private static final int MAX_REQUESTS_PER_MINUTE = 5;
    private static final long MIN_GAP_MS = 300;
    private static final long WINDOW_MS = 60_000;

    private final long[] timestamps = new long[MAX_REQUESTS_PER_MINUTE];
    private int index = 0;
    private long lastRequestTime = 0;

    public synchronized void acquire() throws InterruptedException {
        long now = System.currentTimeMillis();

        // Enforce minimum gap
        long sinceLastRequest = now - lastRequestTime;
        if (sinceLastRequest < MIN_GAP_MS) {
            Thread.sleep(MIN_GAP_MS - sinceLastRequest);
            now = System.currentTimeMillis();
        }

        // Enforce sliding window: if oldest request in window is less than 60s ago, wait
        long oldest = timestamps[index];
        if (oldest > 0) {
            long sinceOldest = now - oldest;
            if (sinceOldest < WINDOW_MS) {
                long waitMs = WINDOW_MS - sinceOldest + 100; // +100ms buffer
                Thread.sleep(waitMs);
                now = System.currentTimeMillis();
            }
        }

        timestamps[index] = now;
        index = (index + 1) % MAX_REQUESTS_PER_MINUTE;
        lastRequestTime = now;
    }
}
