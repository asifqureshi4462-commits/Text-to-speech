package com.asifqureshi.aivoicestudio.network;

import androidx.lifecycle.MutableLiveData;

import com.asifqureshi.aivoicestudio.data.Prefs;
import com.asifqureshi.aivoicestudio.data.SecureTokenStore;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Builds the Retrofit client. Adds the bearer token, refreshes it once on 401, and signals session expiry. */
public final class ApiClient {
    private static final String PLACEHOLDER_URL = "https://server-not-configured.invalid/";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final Prefs prefs;
    private final SecureTokenStore tokens;
    private final Gson gson = new Gson();
    private volatile ApiService service;

    /** True once when the refresh token was rejected; the UI then returns to Login. */
    public final MutableLiveData<Boolean> sessionExpired = new MutableLiveData<>();

    public ApiClient(Prefs prefs, SecureTokenStore tokens) {
        this.prefs = prefs;
        this.tokens = tokens;
    }

    public boolean isConfigured() { return prefs.getNormalizedServerUrl() != null; }

    public ApiService api() {
        ApiService s = service;
        if (s == null) {
            synchronized (this) {
                if (service == null) rebuild();
                s = service;
            }
        }
        return s;
    }

    /** Call after the server URL changes. */
    public synchronized void rebuild() {
        String base = prefs.getNormalizedServerUrl();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(configuredCheck())
                .addInterceptor(bearer())
                .authenticator(new RefreshAuthenticator())
                .build();
        service = new Retrofit.Builder()
                .baseUrl(base != null ? base : PLACEHOLDER_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(ApiService.class);
    }

    private Interceptor configuredCheck() {
        return chain -> {
            if (!isConfigured()) throw new ServerNotConfiguredException();
            return chain.proceed(chain.request());
        };
    }

    private static boolean isPublicAuthPath(String path) {
        return path.contains("/v1/auth/") && !path.endsWith("change-password");
    }

    private Interceptor bearer() {
        return chain -> {
            Request req = chain.request();
            String t = tokens.getAccess();
            if (t != null && !isPublicAuthPath(req.url().encodedPath())) {
                req = req.newBuilder().header("Authorization", "Bearer " + t).build();
            }
            return chain.proceed(req);
        };
    }

    private final class RefreshAuthenticator implements Authenticator {
        @Override
        public Request authenticate(Route route, Response response) throws IOException {
            String sent = response.request().header("Authorization");
            if (sent == null) return null; // login/signup 401s are real credential errors
            int tries = 1;
            for (Response r = response.priorResponse(); r != null; r = r.priorResponse()) tries++;
            if (tries >= 2) return null;
            synchronized (ApiClient.this) {
                String current = tokens.getAccess();
                if (current != null && !sent.equals("Bearer " + current)) {
                    return response.request().newBuilder().header("Authorization", "Bearer " + current).build();
                }
                String refresh = tokens.getRefresh();
                if (refresh == null) return null;
                String base = prefs.getNormalizedServerUrl();
                if (base == null) return null;
                OkHttpClient plain = new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build();
                Request req = new Request.Builder().url(base + "v1/auth/refresh")
                        .post(RequestBody.create(gson.toJson(new ApiModels.RefreshRequest(refresh)), JSON)).build();
                try (Response r = plain.newCall(req).execute()) {
                    if (r.isSuccessful() && r.body() != null) {
                        ApiModels.Envelope<ApiModels.Session> env = gson.fromJson(r.body().charStream(),
                                new com.google.gson.reflect.TypeToken<ApiModels.Envelope<ApiModels.Session>>() { }.getType());
                        if (env != null && env.ok && env.data != null) {
                            tokens.save(env.data.accessToken, env.data.refreshToken);
                            return response.request().newBuilder().header("Authorization", "Bearer " + env.data.accessToken).build();
                        }
                    }
                    if (r.code() == 401 || r.code() == 403) {
                        tokens.clear();
                        sessionExpired.postValue(true);
                    }
                    return null;
                } catch (IOException e) {
                    return null; // offline: keep the session, fail this request only
                }
            }
        }
    }
}
