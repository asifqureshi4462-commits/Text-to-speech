package com.asifqureshi.aivoicestudio;

import android.app.Application;
import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

import com.asifqureshi.aivoicestudio.data.Prefs;
import com.asifqureshi.aivoicestudio.data.SecureTokenStore;
import com.asifqureshi.aivoicestudio.network.ApiClient;
import com.asifqureshi.aivoicestudio.repositories.AuthRepository;
import com.asifqureshi.aivoicestudio.repositories.SpeechRepository;
import com.asifqureshi.aivoicestudio.repositories.VoiceRepository;

/** Application object: owns the long-lived singletons (a lightweight service locator). */
public class App extends Application {
    public Prefs prefs;
    public SecureTokenStore tokens;
    public ApiClient apiClient;
    public AuthRepository authRepository;
    public SpeechRepository speechRepository;
    public VoiceRepository voiceRepository;

    public static App from(Context c) {
        return (App) c.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        tokens = new SecureTokenStore(this);
        apiClient = new ApiClient(prefs, tokens);
        authRepository = new AuthRepository(apiClient, tokens);
        speechRepository = new SpeechRepository(apiClient);
        voiceRepository = new VoiceRepository(apiClient);
        applyTheme(prefs.getThemeMode());
    }

    public static void applyTheme(int mode) {
        AppCompatDelegate.setDefaultNightMode(mode == Prefs.THEME_LIGHT ? AppCompatDelegate.MODE_NIGHT_NO
                : mode == Prefs.THEME_DARK ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
