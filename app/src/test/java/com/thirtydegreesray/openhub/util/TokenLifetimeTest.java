package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TokenLifetimeTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void eightHourAccessToken() {
        assertEquals(Long.valueOf(NOW + 28_800_000L), TokenLifetime.endsAt(NOW, 28_800L));
    }

    @Test
    public void sixMonthRefreshToken() {
        assertEquals(Long.valueOf(NOW + 15_897_600_000L), TokenLifetime.endsAt(NOW, 15_897_600L));
    }

    @Test
    public void noLifetimeMeansItDoesNotExpire() {
        assertNull(TokenLifetime.endsAt(NOW, null));
        assertNull(TokenLifetime.endsAt(NOW, 0L));
        assertNull(TokenLifetime.endsAt(NOW, -5L));
    }

    @Test
    public void hugeValueStaysInTheFuture() {
        assertTrue(TokenLifetime.endsAt(NOW, Long.MAX_VALUE) > NOW);
    }
}
