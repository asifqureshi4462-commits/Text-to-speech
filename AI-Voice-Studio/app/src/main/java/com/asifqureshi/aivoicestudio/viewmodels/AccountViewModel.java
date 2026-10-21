package com.asifqureshi.aivoicestudio.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.asifqureshi.aivoicestudio.App;
import com.asifqureshi.aivoicestudio.network.ApiModels.*;
import com.asifqureshi.aivoicestudio.network.ApiResult;
import com.asifqureshi.aivoicestudio.utils.Background;

import java.util.function.Supplier;

/** Data for Home and Profile: current user, usage, recent audio, favourite voices and account actions. */
public class AccountViewModel extends AndroidViewModel {
    private final App app;

    public final MutableLiveData<Resource<MeData>> me = new MutableLiveData<>();
    public final MutableLiveData<Resource<Usage>> usage = new MutableLiveData<>();
    public final MutableLiveData<Resource<GenerationsData>> recent = new MutableLiveData<>();
    public final MutableLiveData<Resource<FavoritesData>> favorites = new MutableLiveData<>();
    public final MutableLiveData<Resource<MeData>> rename = new MutableLiveData<>();
    public final MutableLiveData<Resource<Object>> changePassword = new MutableLiveData<>();
    public final MutableLiveData<Resource<Object>> deleteAccount = new MutableLiveData<>();

    public AccountViewModel(@NonNull Application application) {
        super(application);
        app = App.from(application);
    }

    private <T> void run(MutableLiveData<Resource<T>> live, Supplier<ApiResult<T>> work) {
        Resource<T> cur = live.getValue();
        if (cur != null && cur.isLoading()) return;
        live.setValue(Resource.loading());
        Background.run(() -> live.postValue(Resource.from(work.get())));
    }

    public void refreshAll() {
        run(me, () -> app.authRepository.me());
        run(usage, () -> app.speechRepository.usage());
        run(recent, () -> app.speechRepository.generations(null, 5, 0));
        run(favorites, () -> app.voiceRepository.favorites());
    }

    public void rename(String name) { run(rename, () -> app.authRepository.updateName(name)); }
    public void changePassword(String cur, String next) { run(changePassword, () -> app.authRepository.changePassword(cur, next)); }
    public void deleteAccount(String password) { run(deleteAccount, () -> app.authRepository.deleteAccount(password)); }

    public void logout(Runnable onDone) {
        Background.run(() -> {
            app.authRepository.logout();
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(onDone);
        });
    }
}
