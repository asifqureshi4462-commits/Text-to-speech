package com.asifqureshi.aivoicestudio.network;

import retrofit2.Call;
import retrofit2.Response;

/** Blocking result wrapper. Never call from the main thread. */
public final class ApiResult<T> {
    public final T data;
    public final ApiFailure failure;

    private ApiResult(T data, ApiFailure failure) { this.data = data; this.failure = failure; }

    public boolean isSuccess() { return failure == null; }
    public static <T> ApiResult<T> success(T data) { return new ApiResult<>(data, null); }
    public static <T> ApiResult<T> failure(ApiFailure f) { return new ApiResult<>(null, f); }

    public static <T> ApiResult<T> execute(Call<ApiModels.Envelope<T>> call) {
        try {
            Response<ApiModels.Envelope<T>> r = call.execute();
            if (r.isSuccessful() && r.body() != null && r.body().ok) return success(r.body().data);
            String body = r.errorBody() != null ? r.errorBody().string() : "";
            return failure(ApiFailure.fromErrorBody(r.code(), body, r.headers().get("Retry-After")));
        } catch (Exception e) {
            return failure(ApiFailure.fromThrowable(e));
        }
    }
}
