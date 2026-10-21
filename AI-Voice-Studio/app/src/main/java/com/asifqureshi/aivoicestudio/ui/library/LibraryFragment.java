package com.asifqureshi.aivoicestudio.ui.library;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.audio.AudioExporter;
import com.asifqureshi.aivoicestudio.network.ApiModels.Generation;
import com.asifqureshi.aivoicestudio.network.ApiModels.Project;
import com.asifqureshi.aivoicestudio.network.ApiModels.Voice;
import com.asifqureshi.aivoicestudio.ui.common.Ui;
import com.asifqureshi.aivoicestudio.ui.main.MainActivity;
import com.asifqureshi.aivoicestudio.viewmodels.LibraryViewModel;
import com.asifqureshi.aivoicestudio.viewmodels.SharedViewModel;
import com.asifqureshi.aivoicestudio.viewmodels.VoicesViewModel;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;

/** Generation history (replay, download, share, regenerate, delete, search) and saved projects. */
public class LibraryFragment extends Fragment implements HistoryAdapter.Actions, ProjectAdapter.Actions {
    private enum Pending { NONE, PLAY, DOWNLOAD, SHARE }

    private LibraryViewModel vm;
    private SharedViewModel shared;
    private AudioExporter exporter;
    private HistoryAdapter historyAdapter;
    private ProjectAdapter projectAdapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    private TabLayout tabs;
    private TextInputEditText etSearch;
    private LinearProgressIndicator progress;
    private View stateBox, btnRetry;
    private TextView textState;
    private RecyclerView recycler;
    private boolean projectsTab, started;
    private Pending pending = Pending.NONE;
    private Generation pendingGeneration;

    @Override public void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        exporter = new AudioExporter(this, (msg, ok) -> { View v = getView(); if (v != null) Ui.toast(v, msg); });
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        vm = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);
        shared = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        tabs = v.findViewById(R.id.tabs); etSearch = v.findViewById(R.id.etSearch); progress = v.findViewById(R.id.progress);
        stateBox = v.findViewById(R.id.stateBox); textState = v.findViewById(R.id.textState);
        btnRetry = v.findViewById(R.id.btnRetry); recycler = v.findViewById(R.id.recycler);

        historyAdapter = new HistoryAdapter(this);
        projectAdapter = new ProjectAdapter(this);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(historyAdapter);
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView r, int dx, int dy) {
                if (dy > 0 && !r.canScrollVertically(1)) { if (projectsTab) vm.loadMoreProjects(); else vm.loadMoreHistory(); }
            }
        });
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                projectsTab = tab.getPosition() == 1;
                recycler.setAdapter(projectsTab ? projectAdapter : historyAdapter);
                etSearch.setText("");
                load();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                pendingSearch = LibraryFragment.this::load;
                handler.postDelayed(pendingSearch, 400);
            }
        });
        btnRetry.setOnClickListener(x -> load());

        vm.history.observe(getViewLifecycleOwner(), s -> {
            if (projectsTab || s == null) return;
            historyAdapter.submitList(new java.util.ArrayList<>(s.items));
            renderState(s.loading, s.failure == null ? null : s.failure.message, s.items.isEmpty(), R.string.history_empty);
        });
        vm.projects.observe(getViewLifecycleOwner(), s -> {
            if (!projectsTab || s == null) return;
            projectAdapter.submitList(new java.util.ArrayList<>(s.items));
            renderState(s.loading, s.failure == null ? null : s.failure.message, s.items.isEmpty(), R.string.projects_empty);
        });
        vm.action.observe(getViewLifecycleOwner(), res -> {
            if (res == null || res.isLoading() || !res.claim()) return;
            if (res.isSuccess()) { if (projectsTab) vm.reloadProjects(); else vm.reloadHistory(); }
            else Ui.failure(requireView(), res.failure, null);
        });
        vm.audioFile.observe(getViewLifecycleOwner(), res -> {
            if (res == null) return;
            progress.setVisibility(res.isLoading() ? View.VISIBLE : View.INVISIBLE);
            if (res.isLoading() || !res.claim()) return;
            if (res.isError()) { Ui.failure(requireView(), res.failure, null); return; }
            File f = res.data;
            switch (pending) {
                case PLAY: PlayerSheet.show(requireContext(), f, pendingGeneration.textPreview == null ? "" : pendingGeneration.textPreview); break;
                case DOWNLOAD: exporter.save(f, "ai-voice-" + pendingGeneration.id.substring(0, 8)); break;
                case SHARE: exporter.share(f); break;
                default: break;
            }
            pending = Pending.NONE;
        });
        vm.fullGeneration.observe(getViewLifecycleOwner(), res -> {
            if (res == null || res.isLoading() || !res.claim()) return;
            if (res.isError()) { Ui.failure(requireView(), res.failure, null); return; }
            Generation g = res.data;
            SharedViewModel.ReuseRequest r = new SharedViewModel.ReuseRequest();
            r.text = g.text != null ? g.text : g.textPreview;
            r.voiceId = g.voiceId; r.voiceName = lookupVoiceName(g.voiceId); r.modelId = g.model; r.settings = g.settings; r.projectId = g.projectId;
            shared.reuse.setValue(r);
            shared.tabRequest.setValue(MainActivity.TAB_CREATE);
        });
        vm.fullProject.observe(getViewLifecycleOwner(), res -> {
            if (res == null || res.isLoading() || !res.claim()) return;
            if (res.isError()) { Ui.failure(requireView(), res.failure, null); return; }
            shared.reuse.setValue(fromProject(res.data));
            shared.tabRequest.setValue(MainActivity.TAB_CREATE);
        });
        if (!isHidden()) ensureLoaded();
    }

    private void ensureLoaded() { if (!started) { started = true; load(); } }

    private void load() {
        started = true;
        String q = Ui.text(etSearch).trim();
        if (projectsTab) vm.loadProjects(q); else vm.loadHistory(q);
    }

    private void renderState(boolean loading, String error, boolean empty, int emptyRes) {
        progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        boolean show = error != null || (empty && !loading);
        stateBox.setVisibility(show ? View.VISIBLE : View.GONE);
        if (error != null) { textState.setText(error); btnRetry.setVisibility(View.VISIBLE); }
        else if (show) { textState.setText(emptyRes); btnRetry.setVisibility(View.GONE); }
    }

    private String lookupVoiceName(String voiceId) {
        VoicesViewModel.State s = new ViewModelProvider(requireActivity()).get(VoicesViewModel.class).state.getValue();
        if (s != null) for (Voice v : s.items) if (v.voiceId.equals(voiceId)) return v.name;
        return voiceId;
    }

    /** Converts a saved project (camelCase request settings) into the normalised keys Create understands. */
    private SharedViewModel.ReuseRequest fromProject(Project p) {
        SharedViewModel.ReuseRequest r = new SharedViewModel.ReuseRequest();
        r.projectId = p.id;
        if (p.content != null && p.content.has("text")) r.text = p.content.get("text").getAsString();
        JsonObject s = p.settings == null ? new JsonObject() : p.settings;
        r.voiceId = str(s, "voiceId"); r.voiceName = r.voiceId == null ? null : lookupVoiceName(r.voiceId);
        r.modelId = str(s, "modelId");
        JsonObject norm = new JsonObject();
        if (s.has("languageCode") && !s.get("languageCode").isJsonNull()) norm.add("languageCode", s.get("languageCode"));
        if (s.has("voiceSettings") && s.get("voiceSettings").isJsonObject()) {
            JsonObject vs = s.getAsJsonObject("voiceSettings");
            copy(vs, "stability", norm, "stability"); copy(vs, "similarityBoost", norm, "similarity_boost");
            copy(vs, "style", norm, "style"); copy(vs, "speed", norm, "speed"); copy(vs, "useSpeakerBoost", norm, "use_speaker_boost");
        }
        r.settings = norm;
        return r;
    }

    private static String str(JsonObject o, String k) { JsonElement e = o.get(k); return e == null || e.isJsonNull() ? null : e.getAsString(); }
    private static void copy(JsonObject from, String fk, JsonObject to, String tk) { if (from.has(fk) && !from.get(fk).isJsonNull()) to.add(tk, from.get(fk)); }

    // ---- history actions ----
    private void fetch(Generation g, Pending what) {
        pending = what; pendingGeneration = g;
        vm.fetchAudio(g);
    }
    @Override public void onPlay(Generation g) { fetch(g, Pending.PLAY); }
    @Override public void onDownload(Generation g) { fetch(g, Pending.DOWNLOAD); }
    @Override public void onShare(Generation g) { fetch(g, Pending.SHARE); }
    @Override public void onReuse(Generation g) { vm.loadFullGeneration(g.id); }
    @Override public void onDelete(Generation g) {
        Ui.confirm(requireContext(), R.string.delete_audio_title, getString(R.string.delete_audio_message), R.string.delete, () -> vm.deleteGeneration(g.id));
    }

    // ---- project actions ----
    @Override public void onOpen(Project p) { vm.loadFullProject(p.id); }
    @Override public void onToggleFavorite(Project p) { vm.toggleProjectFavorite(p.id, !p.isFavorite); }
    @Override public void onMore(Project p, View anchor) {
        PopupMenu m = new PopupMenu(requireContext(), anchor);
        m.getMenu().add(0, 1, 0, R.string.rename);
        m.getMenu().add(0, 2, 1, R.string.duplicate);
        m.getMenu().add(0, 3, 2, R.string.delete);
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) Ui.prompt(requireContext(), R.string.rename, R.string.project_name, p.name, InputType.TYPE_CLASS_TEXT, n -> vm.renameProject(p.id, n));
            else if (item.getItemId() == 2) vm.duplicateProject(p.id);
            else Ui.confirm(requireContext(), R.string.delete_project_title, getString(R.string.delete_project_message, p.name), R.string.delete, () -> vm.deleteProject(p.id));
            return true;
        });
        m.show();
    }

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && vm != null) { if (started) load(); else ensureLoaded(); }
    }

    @Override public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }
}
