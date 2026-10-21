package com.asifqureshi.aivoicestudio.ui.voices;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.audio.PreviewPlayer;
import com.asifqureshi.aivoicestudio.network.ApiModels.Voice;
import com.asifqureshi.aivoicestudio.ui.common.Ui;
import com.asifqureshi.aivoicestudio.ui.main.MainActivity;
import com.asifqureshi.aivoicestudio.viewmodels.SharedViewModel;
import com.asifqureshi.aivoicestudio.viewmodels.VoicesViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Searchable, filterable, paged voice library backed by the real provider (through the backend). */
public class VoicesFragment extends Fragment implements VoiceAdapter.Actions {
    private static final String[] SORT_KEYS = {"name", "name", "created_at_unix"};
    private static final String[] SORT_DIRS = {"asc", "desc", "desc"};
    private static final int[] SORT_LABELS = {R.string.sort_name_asc, R.string.sort_name_desc, R.string.sort_newest};

    private VoicesViewModel vm;
    private SharedViewModel shared;
    private VoiceAdapter adapter;
    private PreviewPlayer preview;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    private TextInputEditText etSearch;
    private ChipGroup chipCategories;
    private Chip chipFavOnly, chipSort;
    private LinearProgressIndicator progress;
    private View stateBox, btnRetry;
    private TextView textState;
    private RecyclerView recycler;
    private int sortIndex = 0;
    private boolean started;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_voices, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        vm = new ViewModelProvider(requireActivity()).get(VoicesViewModel.class);
        shared = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        etSearch = v.findViewById(R.id.etSearch); chipCategories = v.findViewById(R.id.chipCategories);
        chipFavOnly = v.findViewById(R.id.chipFavOnly); chipSort = v.findViewById(R.id.chipSort);
        progress = v.findViewById(R.id.progress); stateBox = v.findViewById(R.id.stateBox);
        textState = v.findViewById(R.id.textState); btnRetry = v.findViewById(R.id.btnRetry); recycler = v.findViewById(R.id.recycler);

        preview = new PreviewPlayer(requireContext(), new PreviewPlayer.Callback() {
            @Override public void onStateChanged(String id) { refreshAdapterState(); }
            @Override public void onError(String id) { Ui.toast(requireView(), getString(R.string.preview_failed)); }
        });
        adapter = new VoiceAdapter(this);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView r, int dx, int dy) {
                if (dy > 0 && !r.canScrollVertically(1)) vm.loadMore();
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                pendingSearch = VoicesFragment.this::applyFilters;
                handler.postDelayed(pendingSearch, 400); // debounce
            }
        });
        chipCategories.setOnCheckedStateChangeListener((g, ids) -> applyFilters());
        chipFavOnly.setOnCheckedChangeListener((b, c) -> render(vm.state.getValue()));
        chipSort.setOnClickListener(x -> new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.sort_by)
                .setItems(new String[]{getString(SORT_LABELS[0]), getString(SORT_LABELS[1]), getString(SORT_LABELS[2])}, (d, which) -> {
                    sortIndex = which;
                    chipSort.setText(SORT_LABELS[which]);
                    applyFilters();
                }).show());
        btnRetry.setOnClickListener(x -> { started = true; vm.reload(); });

        vm.state.observe(getViewLifecycleOwner(), this::render);
        shared.favoriteIds.observe(getViewLifecycleOwner(), f -> refreshAdapterState());
        shared.selectedVoice.observe(getViewLifecycleOwner(), s -> refreshAdapterState());
        shared.favoriteAction.observe(getViewLifecycleOwner(), res -> {
            if (res != null && res.isError() && res.claim()) Ui.failure(requireView(), res.failure, null);
        });
        if (!isHidden()) ensureLoaded();
    }

    private void ensureLoaded() {
        VoicesViewModel.State s = vm.state.getValue();
        if (!started && s != null && s.items.isEmpty() && !s.loading) { started = true; applyFilters(); }
    }

    private String selectedCategory() {
        int id = chipCategories.getCheckedChipId();
        if (id == R.id.chipPremade) return "premade";
        if (id == R.id.chipProfessional) return "professional";
        if (id == R.id.chipCloned) return "cloned";
        if (id == R.id.chipGenerated) return "generated";
        return "";
    }

    private void applyFilters() {
        started = true;
        vm.setFilters(Ui.text(etSearch).trim(), selectedCategory(), SORT_KEYS[sortIndex], SORT_DIRS[sortIndex]);
    }

    private void refreshAdapterState() {
        if (adapter == null) return;
        Set<String> favs = shared.favoriteIds.getValue();
        SharedViewModel.VoiceRef sel = shared.selectedVoice.getValue();
        adapter.setState(favs, sel == null ? null : sel.id, preview == null ? null : preview.getPlayingId());
    }

    private void render(VoicesViewModel.State s) {
        if (s == null || adapter == null) return;
        progress.setVisibility(s.loading ? View.VISIBLE : View.INVISIBLE);
        List<Voice> items = new ArrayList<>(s.items);
        if (chipFavOnly.isChecked()) {
            Set<String> favs = shared.favoriteIds.getValue();
            List<Voice> f = new ArrayList<>();
            for (Voice v : items) if (favs != null && favs.contains(v.voiceId)) f.add(v);
            items = f;
        }
        adapter.submitList(items, this::refreshAdapterState);
        boolean empty = items.isEmpty() && !s.loading;
        stateBox.setVisibility(empty || s.failure != null ? View.VISIBLE : View.GONE);
        if (s.failure != null) {
            textState.setText(s.failure.message);
            btnRetry.setVisibility(View.VISIBLE);
        } else if (empty) {
            textState.setText(chipFavOnly.isChecked() ? R.string.no_favourite_voices_loaded : R.string.no_voices_found);
            btnRetry.setVisibility(View.GONE);
        }
    }

    // ---- VoiceAdapter.Actions ----
    @Override public void onUse(Voice v) {
        shared.selectVoice(v.voiceId, v.name);
        shared.tabRequest.setValue(MainActivity.TAB_CREATE);
    }
    @Override public void onToggleFavorite(Voice v) { shared.toggleFavorite(v.voiceId, v.name); }
    @Override public void onPreview(Voice v) { if (v.previewUrl != null) preview.toggle(v.voiceId, v.previewUrl); }

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) { if (preview != null) preview.stop(); } else if (vm != null) ensureLoaded();
    }

    @Override public void onStop() {
        super.onStop();
        if (preview != null) preview.stop();
    }

    @Override public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        if (preview != null) { preview.stop(); preview = null; }
        super.onDestroyView();
    }
}
