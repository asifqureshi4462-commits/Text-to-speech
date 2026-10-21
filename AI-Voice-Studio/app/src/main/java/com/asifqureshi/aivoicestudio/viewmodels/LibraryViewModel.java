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
import com.asifqureshi.aivoicestudio.utils.Background;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Generation history and saved projects, each paged by offset. */
public class LibraryViewModel extends AndroidViewModel {
    public static final int PAGE = 25;

    public static final class ListState<T> {
        public List<T> items = new ArrayList<>();
        public boolean loading;
        public boolean hasMore;
        public ApiFailure failure;
    }

    private final App app;
    private String historyQuery = "", projectQuery = "";
    private int historyGen, projectGen;

    public final MutableLiveData<ListState<Generation>> history = new MutableLiveData<>(new ListState<>());
    public final MutableLiveData<ListState<Project>> projects = new MutableLiveData<>(new ListState<>());
    public final MutableLiveData<Resource<Object>> action = new MutableLiveData<>();
    public final MutableLiveData<Resource<File>> audioFile = new MutableLiveData<>();
    public final MutableLiveData<Resource<Generation>> fullGeneration = new MutableLiveData<>();
    public final MutableLiveData<Resource<Project>> fullProject = new MutableLiveData<>();

    public LibraryViewModel(@NonNull Application application) {
        super(application);
        app = App.from(application);
    }

    // ---- history ----
    public void loadHistory(String query) { historyQuery = query == null ? "" : query; historyGen++; ListState<Generation> s = new ListState<>(); s.loading = true; history.setValue(s); fetchHistory(historyGen, new ArrayList<>()); }

    public void reloadHistory() { loadHistory(historyQuery); }

    public void loadMoreHistory() {
        ListState<Generation> cur = history.getValue();
        if (cur == null || cur.loading || !cur.hasMore) return;
        ListState<Generation> s = new ListState<>(); s.items = cur.items; s.hasMore = true; s.loading = true; history.setValue(s);
        fetchHistory(historyGen, new ArrayList<>(cur.items));
    }

    private void fetchHistory(int gen, List<Generation> existing) {
        final String q = historyQuery; final int offset = existing.size();
        Background.run(() -> {
            ApiResult<GenerationsData> r = app.speechRepository.generations(q, PAGE, offset);
            if (gen != historyGen) return;
            ListState<Generation> s = new ListState<>(); s.items = existing;
            if (r.isSuccess()) { s.items.addAll(r.data.generations); s.hasMore = s.items.size() < r.data.total; } else s.failure = r.failure;
            history.postValue(s);
        });
    }

    public void deleteGeneration(String id) {
        action.setValue(Resource.loading());
        Background.run(() -> {
            ApiResult<Object> r = app.speechRepository.deleteGeneration(id);
            if (r.isSuccess()) {
                File[] files = AudioFiles.cacheDir(app).listFiles();
                if (files != null) for (File f : files) if (f.getName().startsWith("asset-")) f.delete();
            }
            action.postValue(Resource.from(r));
        });
    }

    public void fetchAudio(Generation g) {
        audioFile.setValue(Resource.loading());
        Background.run(() -> audioFile.postValue(Resource.from(app.speechRepository.downloadAudio(g.audioAssetId, g.outputFormat, AudioFiles.cacheDir(app)))));
    }

    public void loadFullGeneration(String id) {
        fullGeneration.setValue(Resource.loading());
        Background.run(() -> {
            ApiResult<GenerationData> r = app.speechRepository.generation(id);
            fullGeneration.postValue(r.isSuccess() ? Resource.success(r.data.generation) : Resource.error(r.failure));
        });
    }

    // ---- projects ----
    public void loadProjects(String query) { projectQuery = query == null ? "" : query; projectGen++; ListState<Project> s = new ListState<>(); s.loading = true; projects.setValue(s); fetchProjects(projectGen, new ArrayList<>()); }

    public void reloadProjects() { loadProjects(projectQuery); }

    public void loadMoreProjects() {
        ListState<Project> cur = projects.getValue();
        if (cur == null || cur.loading || !cur.hasMore) return;
        ListState<Project> s = new ListState<>(); s.items = cur.items; s.hasMore = true; s.loading = true; projects.setValue(s);
        fetchProjects(projectGen, new ArrayList<>(cur.items));
    }

    private void fetchProjects(int gen, List<Project> existing) {
        final String q = projectQuery; final int offset = existing.size();
        Background.run(() -> {
            ApiResult<ProjectsData> r = app.speechRepository.projects(q, null, PAGE, offset);
            if (gen != projectGen) return;
            ListState<Project> s = new ListState<>(); s.items = existing;
            if (r.isSuccess()) { s.items.addAll(r.data.projects); s.hasMore = s.items.size() < r.data.total; } else s.failure = r.failure;
            projects.postValue(s);
        });
    }

    public void renameProject(String id, String name) {
        ProjectUpdate u = new ProjectUpdate(); u.name = name;
        runAction(() -> { ApiResult<ProjectData> r = app.speechRepository.updateProject(id, u); return r.isSuccess() ? ApiResult.<Object>success(r.data) : ApiResult.<Object>failure(r.failure); });
    }

    public void toggleProjectFavorite(String id, boolean fav) {
        ProjectUpdate u = new ProjectUpdate(); u.isFavorite = fav;
        runAction(() -> { ApiResult<ProjectData> r = app.speechRepository.updateProject(id, u); return r.isSuccess() ? ApiResult.<Object>success(r.data) : ApiResult.<Object>failure(r.failure); });
    }

    public void duplicateProject(String id) {
        runAction(() -> { ApiResult<ProjectData> r = app.speechRepository.duplicateProject(id); return r.isSuccess() ? ApiResult.<Object>success(r.data) : ApiResult.<Object>failure(r.failure); });
    }

    public void deleteProject(String id) { runAction(() -> app.speechRepository.deleteProject(id)); }

    public void loadFullProject(String id) {
        fullProject.setValue(Resource.loading());
        Background.run(() -> {
            ApiResult<ProjectData> r = app.speechRepository.project(id);
            fullProject.postValue(r.isSuccess() ? Resource.success(r.data.project) : Resource.error(r.failure));
        });
    }

    private void runAction(java.util.function.Supplier<ApiResult<Object>> work) {
        action.setValue(Resource.loading());
        Background.run(() -> action.postValue(Resource.from(work.get())));
    }
}
