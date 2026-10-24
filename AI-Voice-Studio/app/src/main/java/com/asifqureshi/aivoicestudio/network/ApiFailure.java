package com.asifqureshi.aivoicestudio.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

/** A user-presentable description of why a request failed. Contains no secrets. */
public final class ApiFailure {
    public final int httpStatus;
    public final String code;
    public final String message;
    public final long retryAfterSec;
    public final JsonObject details;

    public ApiFailure(int httpStatus, String code, String message, long retryAfterSec, JsonObject details) {
        this.httpStatus = httpStatus;
        this.code = code == null ? "unknown" : code;
        this.message = message == null || message.isEmpty() ? "Something went wrong. Please try again." : message;
        this.retryAfterSec = retryAfterSec;
        this.details = details;
    }

    public boolean isNetwork() { return code.equals("network_offline") || code.equals("timeout"); }
    public boolean isCancelled() { return code.equals("cancelled"); }
    public boolean isAuthExpired() { return httpStatus == 401 && (code.equals("unauthenticated") || code.equals("session_expired")); }
    public boolean isRetryable() { return isNetwork() || httpStatus >= 500 || httpStatus == 429; }
    public boolean isQuota() { return code.equals("usage_limit_exceeded") || code.equals("provider_quota_exceeded"); }

    /** Parses the backend's {"ok":false,"error":{...}} body; falls back to a generic message for non-JSON bodies. */
    public static ApiFailure fromErrorBody(int status, String body, String retryAfterHeader) {
        long retry = 0;
        try { if (retryAfterHeader != null) retry = Long.parseLong(retryAfterHeader.trim()); } catch (NumberFormatException ignored) { }
        try {
            ApiModels.Envelope<?> env = new Gson().fromJson(body, ApiModels.Envelope.class);
            if (env != null && env.error != null && env.error.code != null) {
                return new ApiFailure(status, env.error.code, env.error.message, retry, env.error.details);
            }
        } catch (Exception ignored) { }
        String msg = status >= 500 ? "The server had a problem (HTTP " + status + "). Please try again."
                : "The request failed (HTTP " + status + ").";
        return new ApiFailure(status, "http_" + status, msg, retry, null);
    }

    public static ApiFailure fromThrowable(Throwable t) {
        if (t instanceof ServerNotConfiguredException) {
            return new ApiFailure(0, "server_not_configured", "No server address is set. Open Server settings and enter your backend URL.", 0, null);
        }
        String m = t.getMessage() == null ? "" : t.getMessage();
        if (m.equalsIgnoreCase("Canceled") || m.equalsIgnoreCase("Socket closed") || m.contains("closed")) {
            return new ApiFailure(0, "cancelled", "Cancelled.", 0, null);
        }
        if (t instanceof SocketTimeoutException || (t instanceof InterruptedIOException && !m.isEmpty())) {
            return new ApiFailure(0, "timeout", "The server took too long to respond. Check your connection and try again.", 0, null);
        }
        if (t instanceof UnknownHostException || t instanceof ConnectException) {
            return new ApiFailure(0, "network_offline", "Can't reach the server. Check your internet connection and the server address.", 0, null);
        }
        if (t instanceof SSLException) {
            return new ApiFailure(0, "secure_connection_failed", "A secure connection to the server could not be established.", 0, null);
        }
        if (m.contains("ENOSPC") || m.toLowerCase().contains("no space")) {
            return new ApiFailure(0, "storage_full", "Not enough storage space on this device.", 0, null);
        }
        if (t instanceof IOException) {
            return new ApiFailure(0, "network_offline", "A network error occurred. Check your connection and try again.", 0, null);
        }
        return new ApiFailure(0, "unknown", "Something went wrong. Please try again.", 0, null);
    }
}
