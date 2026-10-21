package com.asifqureshi.aivoicestudio.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.network.ApiModels.FavoriteVoice;
import com.asifqureshi.aivoicestudio.network.ApiModels.Generation;
import com.asifqureshi.aivoicestudio.ui.library.HistoryBinder;
import com.asifqureshi.aivoicestudio.ui.main.MainActivity;
import com.asifqureshi.aivoicestudio.utils.Format;
import com.asifqureshi.aivoicestudio.viewmodels.AccountViewModel;
import com.asifqureshi.aivoicestudio.viewmodels.SharedViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Calendar;

public class HomeFragment extends Fragment {
    private AccountViewModel vm;
    private SharedViewModel shared;
    private SwipeRefreshLayout swipe;
    private TextView textGreeting, textAvatar, textUsage, textHomeError, textFavEmpty, textRecentEmpty;
    private LinearProgressIndicator progressUsage;
    private ChipGroup chipFavs;
    private ViewGroup listRecent;
    private String firstName = "";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        vm = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
        shared = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        swipe = v.findViewById(R.id.swipe);
        textGreeting = v.findViewById(R.id.textGreeting); textAvatar = v.findViewById(R.id.textAvatar);
        textUsage = v.findViewById(R.id.textUsage); textHomeError = v.findViewById(R.id.textHomeError);
        textFavEmpty = v.findViewById(R.id.textFavEmpty); textRecentEmpty = v.findViewById(R.id.textRecentEmpty);
        progressUsage = v.findViewById(R.id.progressUsage); chipFavs = v.findViewById(R.id.chipFavs);
        listRecent = v.findViewById(R.id.listRecent);

        swipe.setOnRefreshListener(() -> vm.refreshAll());
        textAvatar.setOnClickListener(x -> shared.tabRequest.setValue(MainActivity.TAB_PROFILE));
        v.findViewById(R.id.btnSearch).setOnClickListener(x -> shared.tabRequest.setValue(MainActivity.TAB_VOICES));
        v.findViewById(R.id.btnQuickCreate).setOnClickListener(x -> shared.tabRequest.setValue(MainActivity.TAB_CREATE));
        v.findViewById(R.id.btnQuickVoices).setOnClickListener(x -> shared.tabRequest.setValue(MainActivity.TAB_VOICES));
        v.findViewById(R.id.btnQuickLibrary).setOnClickListener(x -> shared.tabRequest.setValue(MainActivity.TAB_LIBRARY));

        vm.me.observe(getViewLifecycleOwner(), res -> {
            if (res == null) return;
            if (res.isSuccess()) {
                String name = res.data.user.name == null ? "" : res.data.user.name;
                firstName = name.trim().split("\\s+")[0];
                textAvatar.setText(Format.initials(name));
                textGreeting.setText(getString(greetingRes(), firstName));
                textHomeError.setVisibility(View.GONE);
            } else if (res.isError()) {
                textGreeting.setText(R.string.welcome);
                textHomeError.setText(res.failure.message);
                textHomeError.setVisibility(View.VISIBLE);
            }
            swipe.setRefreshing(res.isLoading() && swipe.isRefreshing());
        });
        vm.usage.observe(getViewLifecycleOwner(), res -> {
            if (res == null) return;
            if (res.isSuccess()) {
                long limit = res.data.limitChars, used = res.data.usedChars;
                progressUsage.setVisibility(View.VISIBLE);
                progressUsage.setProgress(limit > 0 ? (int) Math.min(100, used * 100 / limit) : 0);
                textUsage.setText(getString(R.string.usage_detail, used, limit, res.data.remainingChars, res.data.generationsThisPeriod));
            } else if (res.isError()) {
                progressUsage.setVisibility(View.GONE);
                textUsage.setText(R.string.usage_unavailable);
            }
        });
        vm.favorites.observe(getViewLifecycleOwner(), res -> {
            if (res == null || !res.isSuccess()) return;
            chipFavs.removeAllViews();
            for (FavoriteVoice f : res.data.voices) {
                Chip c = new Chip(requireContext());
                c.setText(f.name != null ? f.name : f.voiceId);
                c.setOnClickListener(x -> { shared.selectVoice(f.voiceId, f.name != null ? f.name : f.voiceId); shared.tabRequest.setValue(MainActivity.TAB_CREATE); });
                chipFavs.addView(c);
            }
            textFavEmpty.setVisibility(res.data.voices.isEmpty() ? View.VISIBLE : View.GONE);
        });
        vm.recent.observe(getViewLifecycleOwner(), res -> {
            if (res == null) return;
            if (!res.isLoading()) swipe.setRefreshing(false);
            if (!res.isSuccess()) return;
            listRecent.removeAllViews();
            for (Generation g : res.data.generations) {
                View row = getLayoutInflater().inflate(R.layout.item_history, listRecent, false);
                HistoryBinder.bind(row, g, false);
                row.setOnClickListener(x -> shared.tabRequest.setValue(MainActivity.TAB_LIBRARY));
                listRecent.addView(row);
            }
            textRecentEmpty.setVisibility(res.data.generations.isEmpty() ? View.VISIBLE : View.GONE);
        });
        vm.refreshAll();
    }

    private int greetingRes() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h < 12 ? R.string.greeting_morning : h < 18 ? R.string.greeting_afternoon : R.string.greeting_evening;
    }

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && vm != null) vm.refreshAll(); // refresh when the tab is shown again
    }
}
