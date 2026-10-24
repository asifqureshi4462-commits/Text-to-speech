package com.asifqureshi.aivoicestudio.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.asifqureshi.aivoicestudio.App;
import com.asifqureshi.aivoicestudio.network.ApiModels.*;
import com.asifqureshi.aivoicestudio.network.ApiResult;
import com.asifqureshi.aivoicestudio.repositories.AuthRepository;
import com.asifqureshi.aivoicestudio.utils.Background;

import java.util.function.Supplier;

public class AuthViewModel extends AndroidViewModel {
    private final AuthRepository repo;

    public final MutableLiveData<Resource<Session>> login = new MutableLiveData<>();
    public final MutableLiveData<Resource<SignupData>> signup = new MutableLiveData<>();
    public final MutableLiveData<Resource<Requested>> forgot = new MutableLiveData<>();
    public final MutableLiveData<Resource<Object>> reset = new MutableLiveData<>();
    public final MutableLiveData<Resource<Object>> verify = new MutableLiveData<>();
    public final MutableLiveData<Resource<Requested>> resend = new MutableLiveData<>();

    public AuthViewModel(@NonNull Application app) {
        super(app);
        repo = App.from(app).authRepository;
    }

    private <T> void run(MutableLiveData<Resource<T>> live, Supplier<ApiResult<T>> work) {
        Resource<T> cur = live.getValue();
        if (cur != null && cur.isLoading()) return; // ignore duplicate taps
        live.setValue(Resource.loading());
        Background.run(() -> live.postValue(Resource.from(work.get())));
    }

    public void login(String email, String password) { run(login, () -> repo.login(email, password)); }
    public void signup(String name, String email, String password) { run(signup, () -> repo.signup(name, email, password)); }
    public void forgot(String email) { run(forgot, () -> repo.forgotPassword(email)); }
    public void reset(String token, String password) { run(reset, () -> repo.resetPassword(token, password)); }
    public void verify(String token) { run(verify, () -> repo.verifyEmail(token)); }
    public void resend(String email) { run(resend, () -> repo.resendVerification(email)); }
}
