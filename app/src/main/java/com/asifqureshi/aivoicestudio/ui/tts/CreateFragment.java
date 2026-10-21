package com.asifqureshi.aivoicestudio.ui.tts;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.asifqureshi.aivoicestudio.App;
import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.audio.AudioExporter;
import com.asifqureshi.aivoicestudio.audio.AudioPlayerView;
import com.asifqureshi.aivoicestudio.network.ApiModels.*;
import com.asifqureshi.aivoicestudio.repositories.SpeechRepository;
import com.asifqureshi.aivoicestudio.ui.common.Ui;
import com.asifqureshi.aivoicestudio.ui.main.MainActivity;
import com.asifqureshi.aivoicestudio.utils.Background;
import com.asifqureshi.aivoicestudio.utils.Format;
import com.asifqureshi.aivoicestudio.viewmodels.Resource;
import com.asifqureshi.aivoicestudio.viewmodels.SharedViewModel;
import com.asifqureshi.aivoicestudio.viewmodels.TtsViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The primary screen: type or import text, pick a voice/model/settings, generate real audio, play, save, share. */
public class CreateFragment extends Fragment {
    private static final int DEFAULT_MAX_CHARS = 5000;
    private static final String DEFAULT_MODEL = "eleven_multilingual_v2";
    private static final int IMPORT_LIMIT_CHARS = 200_000;

    private TtsViewModel vm;
    private SharedViewModel shared;
    private App app;
    private AudioExporter exporter;
    private ActivityResultLauncher<String[]> importLauncher;

    private TextInputEditText etText;
    private TextView textVoiceName, textCounter, textStatus, textErrorMsg, textMeta, textModelsNote;
    private MaterialAutoCompleteTextView dropModel, dropLanguage, dropFormat;
    private TextInputLayout tilLanguage;
    private MaterialSwitch switchOverride, switchBoost;
    private Slider sliderStability, sliderSimilarity, sliderStyle, sliderSpeed;
    private TextView valueStability, valueSimilarity, valueStyle, valueSpeed;
    private View groupVoiceSettings, groupStyle, groupBoost, cardError, cardResult;
    private MaterialButton btnGenerate, btnCancel;
    private LinearProgressIndicator progress;
    private AudioPlayerView player;

    private List<TtsModel> models = new ArrayList<>();
    private List<String> formats = new ArrayList<>();
    private TtsModel selectedModel;
    private String selectedLanguageId; // null = auto-detect
    private String selectedFormat;
    private int serverMaxChars = DEFAULT_MAX_CHARS;
    private String loadedAudioKey;

    @Override public void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        app = App.from(requireContext());
        exporter = new AudioExporter(this, (msg, ok) -> { View v = getView(); if (v != null) Ui.toast(v, msg); });
        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            final Context ctx = requireContext().getApplicationContext();
            Background.run(() -> {
                String text = null;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(ctx.getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    char[] buf = new char[8192];
                    int n;
                    while ((n = r.read(buf)) > 0 && sb.length() < IMPORT_LIMIT_CHARS) sb.append(buf, 0, n);
                    text = sb.toString();
                } catch (Exception ignored) { }
                final String result = text;
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    View v = getView();
                    if (v == null) return;
                    if (result == null) { Ui.toast(v, getString(R.string.import_failed)); return; }
                    etText.setText(result);
                    if (result.length() > maxChars()) Ui.toast(v, getString(R.string.import_too_long, maxChars()));
                });
            });
        });
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_create, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        vm = new ViewModelProvider(requireActivity()).get(TtsViewModel.class);
        shared = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        etText = v.findViewById(R.id.etText);
        textVoiceName = v.findViewById(R.id.textVoiceName); textCounter = v.findViewById(R.id.textCounter);
        textStatus = v.findViewById(R.id.textStatus); textErrorMsg = v.findViewById(R.id.textErrorMsg);
        textMeta = v.findViewById(R.id.textMeta); textModelsNote = v.findViewById(R.id.textModelsNote);
        dropModel = v.findViewById(R.id.dropModel); dropLanguage = v.findViewById(R.id.dropLanguage); dropFormat = v.findViewById(R.id.dropFormat);
        tilLanguage = v.findViewById(R.id.tilLanguage);
        switchOverride = v.findViewById(R.id.switchOverride); switchBoost = v.findViewById(R.id.switchBoost);
        sliderStability = v.findViewById(R.id.sliderStability); sliderSimilarity = v.findViewById(R.id.sliderSimilarity);
        sliderStyle = v.findViewById(R.id.sliderStyle); sliderSpeed = v.findViewById(R.id.sliderSpeed);
        valueStability = v.findViewById(R.id.valueStability); valueSimilarity = v.findViewById(R.id.valueSimilarity);
        valueStyle = v.findViewById(R.id.valueStyle); valueSpeed = v.findViewById(R.id.valueSpeed);
        groupVoiceSettings = v.findViewById(R.id.groupVoiceSettings); groupStyle = v.findViewById(R.id.groupStyle);
        groupBoost = v.findViewById(R.id.groupBoost); cardError = v.findViewById(R.id.cardError); cardResult = v.findViewById(R.id.cardResult);
        btnGenerate = v.findViewById(R.id.btnGenerate); btnCancel = v.findViewById(R.id.btnCancel);
        progress = v.findViewById(R.id.progress); player = v.findViewById(R.id.playerView);

        bindSlider(sliderStability, valueStability);
        bindSlider(sliderSimilarity, valueSimilarity);
        bindSlider(sliderStyle, valueStyle);
        bindSlider(sliderSpeed, valueSpeed);
        switchOverride.setOnCheckedChangeListener((b, on) -> groupVoiceSettings.setVisibility(on ? View.VISIBLE : View.GONE));
        v.findViewById(R.id.btnResetSettings).setOnClickListener(x -> resetSettings());

        v.findViewById(R.id.btnChooseVoice).setOnClickListener(x -> shared.tabRequest.setValue(MainActivity.TAB_VOICES));
        etText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { updateCounter(); }
        });

        v.findViewById(R.id.chipPaste).setOnClickListener(x -> paste());
        v.findViewById(R.id.chipSelectAll).setOnClickListener(x -> { etText.requestFocus(); etText.selectAll(); });
        v.findViewById(R.id.chipClear).setOnClickListener(x -> {
            if (Ui.text(etText).isEmpty()) return;
            Ui.confirm(requireContext(), R.string.clear_text_title, getString(R.string.clear_text_message), R.string.clear, () -> etText.setText(""));
        });
        v.findViewById(R.id.chipImport).setOnClickListener(x -> importLauncher.launch(new String[]{"text/plain"}));
        v.findViewById(R.id.chipSaveDraft).setOnClickListener(x -> {
            app.prefs.setDraft(Ui.text(etText));
            Ui.toast(v, getString(R.string.draft_saved));
        });
        v.findViewById(R.id.chipRestoreDraft).setOnClickListener(x -> restoreDraft(v));

        btnGenerate.setOnClickListener(x -> generate());
        btnCancel.setOnClickListener(x -> vm.cancel());
        v.findViewById(R.id.btnRetry).setOnClickListener(x -> generate());
        v.findViewById(R.id.chipRegenerate).setOnClickListener(x -> regenerate());
        v.findViewById(R.id.chipDownload).setOnClickListener(x -> {
            TtsViewModel.State s = vm.state.getValue();
            if (s != null && s.output != null) exporter.save(s.output.file, "ai-voice-" + shortId(s.output.generationId));
        });
        v.findViewById(R.id.chipShare).setOnClickListener(x -> {
            TtsViewModel.State s = vm.state.getValue();
            if (s != null && s.output != null) exporter.share(s.output.file);
        });
        v.findViewById(R.id.chipSaveProject).setOnClickListener(x -> {
            TtsViewModel.State s = vm.state.getValue();
            if (s == null || s.request == null) return;
            String def = s.request.text.length() > 40 ? s.request.text.substring(0, 40) : s.request.text;
            Ui.prompt(requireContext(), R.string.save_to_library, R.string.project_name, def, InputType.TYPE_CLASS_TEXT,
                    name -> vm.saveAsProject(name, s.request.text, s.request));
        });
        player.setListener(ms -> {
            TtsViewModel.State s = vm.state.getValue();
            if (s != null && s.output != null) vm.reportDuration(s.output.assetId, ms);
        });

        // ---- observers ----
        shared.models.observe(getViewLifecycleOwner(), this::onModels);
        shared.selectedVoice.observe(getViewLifecycleOwner(), voice ->
                textVoiceName.setText(voice == null ? getString(R.string.no_voice_selected) : voice.name));
        shared.reuse.observe(getViewLifecycleOwner(), r -> { if (r != null) { applyReuse(r); shared.reuse.setValue(null); } });
        vm.state.observe(getViewLifecycleOwner(), this::render);
        vm.saveProject.observe(getViewLifecycleOwner(), res -> {
            if (res == null) return;
            if (res.isSuccess() && res.claim()) Ui.toast(v, getString(R.string.saved_to_library));
            else if (res.isError() && res.claim()) Ui.failure(v, res.failure, null);
        });
        updateCounter();
        if (shared.models.getValue() == null) shared.loadModels(false);
    }

    private static String shortId(String id) { return id == null ? "audio" : id.substring(0, Math.min(8, id.length())); }

    private void bindSlider(Slider s, TextView value) {
        value.setText(String.format(java.util.Locale.US, "%.2f", s.getValue()));
        s.addOnChangeListener((sl, val, fromUser) -> value.setText(String.format(java.util.Locale.US, "%.2f", val)));
    }

    private void resetSettings() {
        sliderStability.setValue(0.5f); sliderSimilarity.setValue(0.75f); sliderStyle.setValue(0f); sliderSpeed.setValue(1.0f);
        switchBoost.setChecked(true);
    }

    // ---------- models / dropdowns ----------
    private void onModels(Resource<ModelsData> res) {
        if (res == null) return;
        if (res.isLoading()) return;
        if (res.isError()) {
            textModelsNote.setVisibility(View.VISIBLE);
            textModelsNote.setText(getString(R.string.models_unavailable, res.failure.message));
            if (selectedFormat == null) selectedFormat = app.prefs.getDefaultFormat();
            dropFormat.setText(selectedFormat, false);
            return;
        }
        textModelsNote.setVisibility(View.GONE);
        models = res.data.models == null ? new ArrayList<>() : res.data.models;
        formats = res.data.outputFormats == null ? new ArrayList<>() : res.data.outputFormats;
        if (res.data.maxTextChars > 0) serverMaxChars = res.data.maxTextChars;

        List<String> names = new ArrayList<>();
        for (TtsModel m : models) names.add(m.name != null ? m.name : m.modelId);
        dropModel.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, names));
        dropModel.setOnItemClickListener((p, view, pos, id) -> selectModel(models.get(pos)));
        String pref = app.prefs.getDefaultModelId();
        TtsModel initial = null;
        for (TtsModel m : models) {
            if (selectedModel != null && m.modelId.equals(selectedModel.modelId)) initial = m;
            else if (initial == null && pref != null && m.modelId.equals(pref)) initial = m;
        }
        if (initial == null) for (TtsModel m : models) if (DEFAULT_MODEL.equals(m.modelId)) initial = m;
        if (initial == null && !models.isEmpty()) initial = models.get(0);
        if (initial != null) selectModel(initial);

        dropFormat.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, formats));
        dropFormat.setOnItemClickListener((p, view, pos, id) -> selectedFormat = formats.get(pos));
        String fmtPref = app.prefs.getDefaultFormat();
        if (selectedFormat == null || !formats.contains(selectedFormat)) selectedFormat = formats.contains(fmtPref) ? fmtPref : (formats.isEmpty() ? fmtPref : formats.get(0));
        dropFormat.setText(selectedFormat, false);
        updateCounter();
    }

    private void selectModel(TtsModel m) {
        selectedModel = m;
        dropModel.setText(m.name != null ? m.name : m.modelId, false);
        groupStyle.setVisibility(m.canUseStyle ? View.VISIBLE : View.GONE);   // hide controls the model does not support
        groupBoost.setVisibility(m.canUseSpeakerBoost ? View.VISIBLE : View.GONE);
        final List<ModelLanguage> langs = m.languages == null ? new ArrayList<>() : m.languages;
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.language_auto));
        for (ModelLanguage l : langs) labels.add(l.name != null ? l.name : l.id);
        dropLanguage.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, labels));
        dropLanguage.setOnItemClickListener((p, view, pos, id) -> selectedLanguageId = pos == 0 ? null : langs.get(pos - 1).id);
        selectedLanguageId = null;
        dropLanguage.setText(labels.get(0), false);
        tilLanguage.setVisibility(langs.isEmpty() ? View.GONE : View.VISIBLE);
        updateCounter();
    }

    private int maxChars() {
        int max = serverMaxChars;
        if (selectedModel != null && selectedModel.maxCharacters != null && selectedModel.maxCharacters > 0) max = Math.min(max, selectedModel.maxCharacters);
        return max;
    }

    private void updateCounter() {
        if (textCounter == null) return;
        int n = Ui.text(etText).length(), max = maxChars();
        textCounter.setText(getString(R.string.char_counter, n, max));
        textCounter.setTextColor(n > max ? requireContext().getColor(R.color.app_error) : requireContext().getColor(R.color.app_text_secondary));
    }

    // ---------- editor actions ----------
    private void paste() {
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) { Ui.toast(requireView(), getString(R.string.clipboard_empty)); return; }
        CharSequence t = clip.getItemAt(0).coerceToText(requireContext());
        if (t == null || t.length() == 0) { Ui.toast(requireView(), getString(R.string.clipboard_empty)); return; }
        Editable e = etText.getText();
        int start = Math.max(etText.getSelectionStart(), 0), end = Math.max(etText.getSelectionEnd(), 0);
        if (e == null) etText.setText(t); else e.replace(Math.min(start, end), Math.max(start, end), t);
    }

    private void restoreDraft(View v) {
        final String draft = app.prefs.getDraft();
        if (draft.isEmpty()) { Ui.toast(v, getString(R.string.no_draft)); return; }
        if (Ui.text(etText).isEmpty()) { etText.setText(draft); return; }
        Ui.confirm(requireContext(), R.string.restore_draft, getString(R.string.restore_draft_message), R.string.restore, () -> etText.setText(draft));
    }

    private void applyReuse(SharedViewModel.ReuseRequest r) {
        if (r.text != null) etText.setText(r.text);
        if (r.voiceId != null) shared.selectVoice(r.voiceId, r.voiceName != null ? r.voiceName : r.voiceId);
        if (r.modelId != null) for (TtsModel m : models) if (m.modelId.equals(r.modelId)) selectModel(m);
        if (r.settings != null) {
            boolean any = false;
            any |= setSlider(sliderStability, r.settings, "stability");
            any |= setSlider(sliderSimilarity, r.settings, "similarity_boost");
            any |= setSlider(sliderStyle, r.settings, "style");
            any |= setSlider(sliderSpeed, r.settings, "speed");
            JsonElement boost = r.settings.get("use_speaker_boost");
            if (boost != null && !boost.isJsonNull()) { switchBoost.setChecked(boost.getAsBoolean()); any = true; }
            JsonElement lang = r.settings.get("languageCode");
            if (lang != null && !lang.isJsonNull()) { selectedLanguageId = lang.getAsString(); dropLanguage.setText(selectedLanguageId, false); }
            switchOverride.setChecked(any);
        }
    }

    private boolean setSlider(Slider s, JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return false;
        float val = Math.max(s.getValueFrom(), Math.min(s.getValueTo(), e.getAsFloat()));
        s.setValue(Math.round(val / s.getStepSize()) * s.getStepSize());
        return true;
    }

    // ---------- generation ----------
    private TtsRequest buildRequest() {
        View v = requireView();
        SharedViewModel.VoiceRef voice = shared.selectedVoice.getValue();
        String text = Ui.text(etText);
        if (voice == null) { Ui.toast(v, getString(R.string.error_no_voice)); return null; }
        if (text.trim().isEmpty()) { Ui.toast(v, getString(R.string.error_no_text)); return null; }
        if (text.length() > maxChars()) { Ui.toast(v, getString(R.string.error_text_too_long, maxChars())); return null; }
        TtsRequest r = new TtsRequest();
        r.text = text; r.voiceId = voice.id;
        r.modelId = selectedModel != null ? selectedModel.modelId : DEFAULT_MODEL;
        r.outputFormat = selectedFormat != null ? selectedFormat : app.prefs.getDefaultFormat();
        r.languageCode = selectedLanguageId;
        if (switchOverride.isChecked()) {
            VoiceSettings vs = new VoiceSettings();
            vs.stability = sliderStability.getValue();
            vs.similarityBoost = sliderSimilarity.getValue();
            if (selectedModel == null || selectedModel.canUseStyle) vs.style = sliderStyle.getValue();
            if (sliderSpeed.getValue() != 1.0f) vs.speed = sliderSpeed.getValue();
            if (selectedModel == null || selectedModel.canUseSpeakerBoost) vs.useSpeakerBoost = switchBoost.isChecked();
            r.voiceSettings = vs;
        }
        return r;
    }

    private void generate() {
        if (vm.isGenerating()) return;
        TtsRequest r = buildRequest();
        if (r == null) return;
        SharedViewModel.VoiceRef voice = shared.selectedVoice.getValue();
        vm.generate(r, voice == null ? "" : voice.name);
    }

    private void regenerate() {
        TtsViewModel.State s = vm.state.getValue();
        if (s == null || s.request == null || vm.isGenerating()) return;
        vm.generate(s.request, s.voiceName);
    }

    private void render(TtsViewModel.State s) {
        if (s == null) return;
        boolean busy = s.phase == TtsViewModel.Phase.GENERATING;
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        textStatus.setVisibility(busy ? View.VISIBLE : View.GONE);
        textStatus.setText(R.string.generating_status);
        btnGenerate.setEnabled(!busy);
        btnCancel.setVisibility(busy ? View.VISIBLE : View.GONE);
        cardError.setVisibility(s.phase == TtsViewModel.Phase.ERROR ? View.VISIBLE : View.GONE);
        if (s.phase == TtsViewModel.Phase.ERROR && s.failure != null) {
            String msg = s.failure.message;
            if (s.failure.retryAfterSec > 0) msg += " " + getString(R.string.retry_after, (int) s.failure.retryAfterSec);
            textErrorMsg.setText(msg);
            requireView().findViewById(R.id.btnRetry).setVisibility(s.failure.isRetryable() ? View.VISIBLE : View.GONE);
        }
        boolean ok = s.phase == TtsViewModel.Phase.SUCCESS && s.output != null;
        cardResult.setVisibility(ok ? View.VISIBLE : View.GONE);
        if (ok) {
            String key = s.output.file.getAbsolutePath();
            if (!key.equals(loadedAudioKey)) {
                loadedAudioKey = key;
                player.load(s.output.file, false);
            }
            StringBuilder sb = new StringBuilder();
            sb.append(getString(R.string.meta_line, s.request.modelId, s.voiceName, s.request.text.length(), s.request.outputFormat, Format.fileSize(s.output.bytes)));
            if (s.output.usageRemaining >= 0) sb.append("\n").append(getString(R.string.usage_remaining, s.output.usageRemaining));
            textMeta.setText(sb.toString());
        } else if (!ok) {
            player.pause();
        }
    }

    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden && player != null) player.pause();
    }

    @Override public void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override public void onDestroyView() {
        if (player != null) player.release();
        loadedAudioKey = null;
        super.onDestroyView();
    }
}
