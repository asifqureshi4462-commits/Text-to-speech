package com.asifqureshi.aivoicestudio.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.asifqureshi.aivoicestudio.App;
import com.asifqureshi.aivoicestudio.network.ApiModels.*;
import com.asifqureshi.aivoicestudio.utils.Background;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;

/** Activity-scoped state shared between the bottom-navigation tabs. */
public class SharedViewModel extends AndroidViewModel {
    public static final class VoiceRef {
        public final String id, name;
        public VoiceRef(String id, String name) { this.id = id; this.name = name; }
    }

    /** A request to open Create pre-filled (from History or Projects). */
    public static final class ReuseRequest {
        public String text, voiceId, voiceName, modelId, projectId;
        public JsonObject settings;
    }

    private final App app;

    public final MutableLiveData<VoiceRef> selectedVoice = new MutableLiveData<>();
    public final MutableLiveData<Set<String>> favoriteIds = new MutableLiveData<>(new HashSet<>());
    public final MutableLiveData<Resource<ModelsData>> models = new MutableLiveData<>();
    public final MutableLiveData<Integer> tabRequest = new MutableLiveData<>();
    public final MutableLiveData<ReuseRequest> reuse = new MutableLiveData<>();
    public final MutableLiveData<Resource<Object>> favoriteAction = new MutableLiveData<>();

    public SharedViewModel(@NonNull Application application) {
        super(application);
        app = App.from(application);
        String id = app.prefs.getDefaultVoiceId();
        if (id != null) selectedVoice.setValue(new VoiceRef(id, app.prefs.getDefaultVoiceName()));
    }

    public void selectVoice(String id, String name) {
        selectedVoice.setValue(new VoiceRef(id, name));
        app.prefs.setDefaultVoice(id, name);
    }

    public void loadModels(boolean force) {
        Resource<ModelsData> cur = models.getValue();
        if (cur != null && (cur.isLoading() || (cur.isSuccess() && !force))) return;
        models.setValue(Resource.loading());
        Background.run(() -> models.postValue(Resource.from(app.voiceRepository.models())));
    }

    public void loadFavorites() {
        Background.run(() -> {
            com.asifqureshi.aivoicestudio.network.ApiResult<FavoritesData> r = app.voiceRepository.favorites();
            if (r.isSuccess()) {
                Set<String> ids = new HashSet<>();
                for (FavoriteVoice v : r.data.voices) ids.add(v.voiceId);
                favoriteIds.postValue(ids);
            }
        });
    }

    public void toggleFavorite(String voiceId, String name) {
        Set<String> cur = favoriteIds.getValue() == null ? new HashSet<>() : new HashSet<>(favoriteIds.getValue());
        final boolean add = !cur.contains(voiceId);
        if (add) cur.add(voiceId); else cur.remove(voiceId);
        favoriteIds.setValue(cur); // optimistic
        Background.run(() -> {
            com.asifqureshi.aivoicestudio.network.ApiResult<Object> r = add ? app.voiceRepository.addFavorite(voiceId, name) : app.voiceRepository.removeFavorite(voiceId);
            if (!r.isSuccess()) {
                Set<String> rollback = favoriteIds.getValue() == null ? new HashSet<>() : new HashSet<>(favoriteIds.getValue());
                if (add) rollback.remove(voiceId); else rollback.add(voiceId);
                favoriteIds.postValue(rollback);
                favoriteAction.postValue(Resource.error(r.failure));
            }
        });
    }
}
