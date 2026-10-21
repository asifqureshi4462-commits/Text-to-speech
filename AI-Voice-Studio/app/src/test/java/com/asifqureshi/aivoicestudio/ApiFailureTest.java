package com.asifqureshi.aivoicestudio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.asifqureshi.aivoicestudio.network.ApiFailure;
import com.asifqureshi.aivoicestudio.network.ServerNotConfiguredException;

import org.junit.Test;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class ApiFailureTest {
    @Test public void parsesBackendErrorEnvelope() {
        String body = "{\"ok\":false,\"error\":{\"code\":\"usage_limit_exceeded\",\"message\":\"Over the limit\",\"details\":{\"limit\":300,\"used\":290}}}";
        ApiFailure f = ApiFailure.fromErrorBody(402, body, null);
        assertEquals("usage_limit_exceeded", f.code);
        assertEquals("Over the limit", f.message);
        assertEquals(402, f.httpStatus);
        assertTrue(f.isQuota());
        assertEquals(300, f.details.get("limit").getAsInt());
        assertFalse(f.isRetryable());
    }

    @Test public void rateLimitCarriesRetryAfterAndIsRetryable() {
        ApiFailure f = ApiFailure.fromErrorBody(429, "{\"ok\":false,\"error\":{\"code\":\"rate_limited\",\"message\":\"Slow down\"}}", "7");
        assertEquals(7, f.retryAfterSec);
        assertTrue(f.isRetryable());
    }

    @Test public void authExpiryDetected() {
        ApiFailure f = ApiFailure.fromErrorBody(401, "{\"ok\":false,\"error\":{\"code\":\"unauthenticated\",\"message\":\"x\"}}", null);
        assertTrue(f.isAuthExpired());
        ApiFailure bad = ApiFailure.fromErrorBody(401, "{\"ok\":false,\"error\":{\"code\":\"invalid_credentials\",\"message\":\"x\"}}", null);
        assertFalse(bad.isAuthExpired());
    }

    @Test public void nonJsonBodiesGetGenericMessages() {
        ApiFailure f = ApiFailure.fromErrorBody(502, "<html>Bad gateway</html>", null);
        assertEquals("http_502", f.code);
        assertTrue(f.isRetryable());
        assertTrue(f.message.contains("502"));
        ApiFailure empty = ApiFailure.fromErrorBody(404, "", null);
        assertEquals("http_404", empty.code);
    }

    @Test public void throwablesMapToFriendlyCodes() {
        assertEquals("network_offline", ApiFailure.fromThrowable(new UnknownHostException("x")).code);
        assertEquals("timeout", ApiFailure.fromThrowable(new SocketTimeoutException("timeout")).code);
        assertEquals("server_not_configured", ApiFailure.fromThrowable(new ServerNotConfiguredException()).code);
        assertEquals("cancelled", ApiFailure.fromThrowable(new java.io.IOException("Canceled")).code);
        assertEquals("storage_full", ApiFailure.fromThrowable(new java.io.IOException("write failed: ENOSPC (No space left on device)")).code);
        assertTrue(ApiFailure.fromThrowable(new UnknownHostException("x")).isNetwork());
    }
}
