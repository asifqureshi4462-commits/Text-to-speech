package com.asifqureshi.aivoicestudio.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.asifqureshi.aivoicestudio.App;
import com.asifqureshi.aivoicestudio.audio.AudioFiles;
import com.asifqureshi.aivoicestudio.network.ApiFailure;
import com.asifqureshi.aivoicestudio.network.ApiModels.*;
import com.asifqureshi.aivoicestudio.network.ApiResult;
import com.asifqureshi.aivoicestudio.repositories.SpeechRepository;
import com.asifqureshi.aivoicestudio.utils.Background;

public class TtsViewModel extends AndroidViewModel {
    public enum Phase { IDLE, GENERATING, SUCCESS, ERROR }

    public static final class State {
        public Phase phase = Phase.IDLE;
        public SpeechRepository.TtsOutput output;
        public ApiFailure failure;
        public TtsRequest request;
        public String voiceName;
        public long finishedAt;
    }

    private final App app;
    private SpeechRepository.CancelHandle handle;
    public final MutableLiveData<State> state = new MutableLiveData<>(new State());
    public final MutableLiveData<Resource<ProjectData>> saveProject = new MutableLiveData<>();

    public TtsViewModel(@NonNull Application application) {
        super(application);
        app = App.from(application);
    }

    public boolean isGenerating() { return state.getValue() != null && state.getValue().phase == Phase.GENERATING; }

    public void generate(TtsRequest req, String voiceName) {
        if (isGenerating()) return; // prevents duplicate requests
        State s = new State();
        s.phase = Phase.GENERATING; s.request = req; s.voiceName = voiceName;
        state.setValue(s);
        final SpeechRepository.CancelHandle h = new SpeechRepository.CancelHandle();
        handle = h;
        Background.run(() -> {
            ApiResult<SpeechRepository.TtsOutput> r = app.speechRepository.synthesize(req, AudioFiles.cacheDir(app), h);
            State done = new State();
            done.request = req; done.voiceName = voiceName; done.finishedAt = System.currentTimeMillis();
            if (r.isSuccess()) { done.phase = Phase.SUCCESS; done.output = r.data; }
            else if (r.failure.isCancelled()) { done.phase = Phase.IDLE; }
            else { done.phase = Phase.ERROR; done.failure = r.failure; }
            state.postValue(done);
        });
    }

    public void cancel() {
        if (handle != null) handle.cancel();
    }

    public void reset() {
        if (!isGenerating()) state.setValue(new State());
    }

    public void reportDuration(String assetId, long ms) {
        if (assetId == null || ms <= 0) return;
        Background.run(() -> app.speechRepository.reportDuration(assetId, ms));
    }

    public void saveAsProject(String name, String text, TtsRequest req) {
        ProjectCreate body = new ProjectCreate();
        body.name = name; body.type = "tts"; body.isDraft = false;
        body.content = new com.google.gson.JsonObject();
        body.content.addProperty("text", text);
        body.settings = com.google.gson.JsonParser.parseString(new com.google.gson.Gson().toJson(req)).getAsJsonObject();
        body.settings.remove("text");
        saveProject.setValue(Resource.loading());
        Background.run(() -> saveProject.postValue(Resource.from(app.speechRepository.createProject(body))));
    }
}
