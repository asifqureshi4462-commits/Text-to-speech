package com.asifqureshi.aivoicestudio.viewmodels;

import com.asifqureshi.aivoicestudio.network.ApiFailure;
import com.asifqureshi.aivoicestudio.network.ApiResult;

/** Loading / success / error wrapper exposed by ViewModels. {@link #claim()} makes one-shot results safe across rotation. */
public final class Resource<T> {
    public enum Status { LOADING, SUCCESS, ERROR }

    public final Status status;
    public final T data;
    public final ApiFailure failure;
    private boolean claimed;

    private Resource(Status s, T d, ApiFailure f) { status = s; data = d; failure = f; }

    public static <T> Resource<T> loading() { return new Resource<>(Status.LOADING, null, null); }
    public static <T> Resource<T> success(T d) { return new Resource<>(Status.SUCCESS, d, null); }
    public static <T> Resource<T> error(ApiFailure f) { return new Resource<>(Status.ERROR, null, f); }
    public static <T> Resource<T> from(ApiResult<T> r) { return r.isSuccess() ? success(r.data) : error(r.failure); }

    public boolean isLoading() { return status == Status.LOADING; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isError() { return status == Status.ERROR; }

    /** Returns true only the first time it is called (for one-shot navigation / toasts). */
    public synchronized boolean claim() {
        if (claimed) return false;
        claimed = true;
        return true;
    }
}
