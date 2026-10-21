package com.asifqureshi.aivoicestudio.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.asifqureshi.aivoicestudio.BuildConfig;
import com.asifqureshi.aivoicestudio.utils.Validators;

/** Non-sensitive app settings. Tokens are stored separately in {@link SecureTokenStore}. */
public final class Prefs {
    public static final int THEME_SYSTEM = 0, THEME_LIGHT = 1, THEME_DARK = 2;
    public static final String DEFAULT_FORMAT = "mp3_44100_128";

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public boolean isOnboardingDone() { return sp.getBoolean("onboarding_done", false); }
    public void setOnboardingDone(boolean v) { sp.edit().putBoolean("onboarding_done", v).apply(); }

    public int getThemeMode() { return sp.getInt("theme_mode", THEME_SYSTEM); }
    public void setThemeMode(int v) { sp.edit().putInt("theme_mode", v).apply(); }

    /** User-entered server URL, falling back to the build-time default. May be empty. */
    public String getServerUrl() {
        String saved = sp.getString("server_url", "");
        return saved.isEmpty() ? BuildConfig.DEFAULT_BACKEND_URL : saved;
    }
    public String getNormalizedServerUrl() { return Validators.normalizeServerUrl(getServerUrl()); }
    public void setServerUrl(String v) { sp.edit().putString("server_url", v == null ? "" : v).apply(); }

    public String getDefaultVoiceId() { return sp.getString("default_voice_id", null); }
    public String getDefaultVoiceName() { return sp.getString("default_voice_name", null); }
    public void setDefaultVoice(String id, String name) {
        sp.edit().putString("default_voice_id", id).putString("default_voice_name", name).apply();
    }

    public String getDefaultModelId() { return sp.getString("default_model_id", null); }
    public void setDefaultModelId(String v) { sp.edit().putString("default_model_id", v).apply(); }

    public String getDefaultFormat() { return sp.getString("default_format", DEFAULT_FORMAT); }
    public void setDefaultFormat(String v) { sp.edit().putString("default_format", v).apply(); }

    public float getPlaybackSpeed() { return sp.getFloat("playback_speed", 1.0f); }
    public void setPlaybackSpeed(float v) { sp.edit().putFloat("playback_speed", v).apply(); }

    public String getDraft() { return sp.getString("draft_text", ""); }
    public void setDraft(String v) { sp.edit().putString("draft_text", v == null ? "" : v).apply(); }
}
