package com.asifqureshi.aivoicestudio.repositories;

import com.asifqureshi.aivoicestudio.data.SecureTokenStore;
import com.asifqureshi.aivoicestudio.network.ApiClient;
import com.asifqureshi.aivoicestudio.network.ApiModels.*;
import com.asifqureshi.aivoicestudio.network.ApiResult;

/** Account operations. All methods block: call from a background thread. */
public final class AuthRepository {
    private final ApiClient client;
    private final SecureTokenStore tokens;

    public AuthRepository(ApiClient client, SecureTokenStore tokens) {
        this.client = client;
        this.tokens = tokens;
    }

    public boolean hasSession() { return tokens.hasSession(); }

    public ApiResult<SignupData> signup(String name, String email, String password) {
        ApiResult<SignupData> r = ApiResult.execute(client.api().signup(new SignupRequest(name, email, password, true)));
        if (r.isSuccess() && r.data.session != null) tokens.save(r.data.session.accessToken, r.data.session.refreshToken);
        return r;
    }

    public ApiResult<Session> login(String email, String password) {
        ApiResult<Session> r = ApiResult.execute(client.api().login(new LoginRequest(email, password)));
        if (r.isSuccess()) tokens.save(r.data.accessToken, r.data.refreshToken);
        return r;
    }

    public void logout() {
        String refresh = tokens.getRefresh();
        if (refresh != null) {
            try { ApiResult.execute(client.api().logout(new RefreshRequest(refresh))); } catch (Exception ignored) { }
        }
        tokens.clear();
    }

    public void clearLocalSession() { tokens.clear(); }

    public ApiResult<Requested> forgotPassword(String email) { return ApiResult.execute(client.api().forgotPassword(new EmailRequest(email))); }
    public ApiResult<Object> resetPassword(String token, String pw) { return ApiResult.execute(client.api().resetPassword(new ResetRequest(token, pw))); }
    public ApiResult<Object> verifyEmail(String token) { return ApiResult.execute(client.api().verifyEmail(new TokenRequest(token))); }
    public ApiResult<Requested> resendVerification(String email) { return ApiResult.execute(client.api().resendVerification(new EmailRequest(email))); }

    public ApiResult<Object> changePassword(String current, String next) {
        ApiResult<Object> r = ApiResult.execute(client.api().changePassword(new ChangePasswordRequest(current, next)));
        if (r.isSuccess()) tokens.clear(); // server revoked every session; sign in again
        return r;
    }

    public ApiResult<MeData> me() { return ApiResult.execute(client.api().me()); }
    public ApiResult<MeData> updateName(String name) { return ApiResult.execute(client.api().updateName(new NameRequest(name))); }

    public ApiResult<Object> deleteAccount(String password) {
        ApiResult<Object> r = ApiResult.execute(client.api().deleteAccount(new PasswordRequest(password)));
        if (r.isSuccess()) tokens.clear();
        return r;
    }
}
