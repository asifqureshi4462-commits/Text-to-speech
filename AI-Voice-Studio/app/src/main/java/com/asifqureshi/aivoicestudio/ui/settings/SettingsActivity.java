package com.asifqureshi.aivoicestudio.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.asifqureshi.aivoicestudio.App;
import com.asifqureshi.aivoicestudio.BuildConfig;
import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.audio.AudioFiles;
import com.asifqureshi.aivoicestudio.data.Prefs;
import com.asifqureshi.aivoicestudio.network.ApiModels.ModelsData;
import com.asifqureshi.aivoicestudio.network.ApiModels.TtsModel;
import com.asifqureshi.aivoicestudio.network.ApiResult;
import com.asifqureshi.aivoicestudio.ui.common.EdgeActivity;
import com.asifqureshi.aivoicestudio.ui.common.Ui;
import com.asifqureshi.aivoicestudio.utils.Background;
import com.asifqureshi.aivoicestudio.utils.Format;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Settings persisted in {@link Prefs}; each value is read by the feature it affects (Create, player, theme, networking). */
public class SettingsActivity extends EdgeActivity {
    private App app;
    private ViewGroup list;
    private final List<Runnable> refreshers = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEdgeContentView(R.layout.activity_settings);
        app = App.from(this);
        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setTitle(R.string.settings);
        tb.setNavigationIcon(R.drawable.ic_back);
        tb.setNavigationContentDescription(R.string.back);
        tb.setNavigationOnClickListener(v -> finish());
        list = findViewById(R.id.list);

        row(R.string.setting_theme, this::themeLabel, this::chooseTheme);
        row(R.string.setting_default_voice, () -> app.prefs.getDefaultVoiceName() == null ? getString(R.string.none_set) : app.prefs.getDefaultVoiceName(), this::clearDefaultVoice);
        row(R.string.setting_default_model, () -> app.prefs.getDefaultModelId() == null ? getString(R.string.automatic) : app.prefs.getDefaultModelId(), this::chooseModel);
        row(R.string.setting_default_format, () -> app.prefs.getDefaultFormat(), this::chooseFormat);
        row(R.string.setting_server, () -> app.prefs.getServerUrl().isEmpty() ? getString(R.string.not_configured) : app.prefs.getServerUrl(), () -> Ui.serverSettings(this, this::refreshAll));
        row(R.string.setting_cache, () -> Format.fileSize(AudioFiles.cacheSize(this)), this::clearCache);
        row(R.string.terms, () -> "", () -> LegalActivity.open(this, LegalActivity.TERMS));
        row(R.string.privacy, () -> "", () -> LegalActivity.open(this, LegalActivity.PRIVACY));
        row(R.string.open_source_licenses, () -> "", () -> LegalActivity.open(this, LegalActivity.LICENSES));
        row(R.string.app_version, () -> BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")", null);
        row(R.string.developer, () -> getString(R.string.developer_name), null);
    }

    private void row(int titleRes, Supplier<String> value, Runnable onClick) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_setting, list, false);
        ((TextView) v.findViewById(R.id.settingTitle)).setText(titleRes);
        final TextView val = v.findViewById(R.id.settingValue);
        Runnable refresh = () -> { String s = value.get(); val.setText(s); val.setVisibility(s == null || s.isEmpty() ? View.GONE : View.VISIBLE); };
        refresh.run();
        refreshers.add(refresh);
        if (onClick != null) v.setOnClickListener(x -> onClick.run());
        else { v.setClickable(false); v.setBackground(null); }
        list.addView(v);
    }

    private void refreshAll() { for (Runnable r : refreshers) r.run(); }

    private String themeLabel() {
        int m = app.prefs.getThemeMode();
        return getString(m == Prefs.THEME_LIGHT ? R.string.theme_light : m == Prefs.THEME_DARK ? R.string.theme_dark : R.string.theme_system);
    }

    private void chooseTheme() {
        String[] items = {getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark)};
        new MaterialAlertDialogBuilder(this).setTitle(R.string.setting_theme).setSingleChoiceItems(items, app.prefs.getThemeMode(), (d, which) -> {
            app.prefs.setThemeMode(which);
            d.dismiss();
            App.applyTheme(which); // recreates activities with the new theme
        }).show();
    }

    private void clearDefaultVoice() {
        if (app.prefs.getDefaultVoiceId() == null) { Ui.toast(findViewById(R.id.root), getString(R.string.default_voice_hint)); return; }
        Ui.confirm(this, R.string.setting_default_voice, getString(R.string.clear_default_voice_message), R.string.clear, () -> {
            app.prefs.setDefaultVoice(null, null);
            refreshAll();
        });
    }

    private void chooseFormat() {
        final String[] formats = {"mp3_44100_128", "mp3_44100_96", "mp3_44100_64", "mp3_44100_32", "mp3_22050_32", "opus_48000_128", "opus_48000_64"};
        int sel = 0;
        for (int i = 0; i < formats.length; i++) if (formats[i].equals(app.prefs.getDefaultFormat())) sel = i;
        new MaterialAlertDialogBuilder(this).setTitle(R.string.setting_default_format).setSingleChoiceItems(formats, sel, (d, which) -> {
            app.prefs.setDefaultFormat(formats[which]);
            d.dismiss();
            refreshAll();
        }).show();
    }

    private void chooseModel() {
        final View root = findViewById(R.id.root);
        Background.run(() -> {
            ApiResult<ModelsData> r = app.voiceRepository.models();
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (!r.isSuccess()) { Ui.failure(root, r.failure, null); return; }
                final List<TtsModel> models = r.data.models;
                final String[] names = new String[models.size() + 1];
                names[0] = getString(R.string.automatic);
                for (int i = 0; i < models.size(); i++) names[i + 1] = models.get(i).name != null ? models.get(i).name : models.get(i).modelId;
                new MaterialAlertDialogBuilder(this).setTitle(R.string.setting_default_model).setItems(names, (d, which) -> {
                    app.prefs.setDefaultModelId(which == 0 ? null : models.get(which - 1).modelId);
                    refreshAll();
                }).show();
            });
        });
    }

    private void clearCache() {
        Ui.confirm(this, R.string.setting_cache, getString(R.string.clear_cache_message), R.string.clear, () -> {
            AudioFiles.clear(this);
            refreshAll();
        });
    }
}
