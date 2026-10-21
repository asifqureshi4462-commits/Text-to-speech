package com.asifqureshi.aivoicestudio.ui.splash;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.asifqureshi.aivoicestudio.App;
import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.network.ApiModels.MeData;
import com.asifqureshi.aivoicestudio.network.ApiResult;
import com.asifqureshi.aivoicestudio.ui.auth.LoginActivity;
import com.asifqureshi.aivoicestudio.ui.main.MainActivity;
import com.asifqureshi.aivoicestudio.ui.onboarding.OnboardingActivity;
import com.asifqureshi.aivoicestudio.utils.Background;

/**
 * First screen. Shows the brand logo immediately (system splash + this screen) and routes as soon as the
 * auth state is known: no artificial delay. A stored session is validated with one /v1/me call.
 */
public class SplashActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        route();
    }

    private void go(Class<?> target, boolean sessionExpired) {
        if (isFinishing() || isDestroyed()) return;
        Intent i = new Intent(this, target);
        if (sessionExpired) i.putExtra(LoginActivity.EXTRA_SESSION_EXPIRED, true);
        startActivity(i);
        finish();
    }

    private void route() {
        final App app = App.from(this);
        if (!app.authRepository.hasSession()) {
            go(app.prefs.isOnboardingDone() ? LoginActivity.class : OnboardingActivity.class, false);
            return;
        }
        if (!app.apiClient.isConfigured()) { go(LoginActivity.class, false); return; }
        Background.run(() -> {
            ApiResult<MeData> r = app.authRepository.me();
            runOnUiThread(() -> {
                if (r.isSuccess()) go(MainActivity.class, false);
                else if (r.failure.isAuthExpired() || !app.authRepository.hasSession()) {
                    app.authRepository.clearLocalSession();
                    go(LoginActivity.class, true);
                } else {
                    go(MainActivity.class, false); // offline / server error: open the app, screens show retry states
                }
            });
        });
    }
}
