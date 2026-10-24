package com.asifqureshi.aivoicestudio.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import com.asifqureshi.aivoicestudio.App;
import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.ui.common.EdgeActivity;
import com.asifqureshi.aivoicestudio.ui.common.Ui;
import com.asifqureshi.aivoicestudio.ui.main.MainActivity;
import com.asifqureshi.aivoicestudio.ui.settings.LegalActivity;
import com.asifqureshi.aivoicestudio.utils.Validators;
import com.asifqureshi.aivoicestudio.viewmodels.AuthViewModel;
import com.asifqureshi.aivoicestudio.viewmodels.Resource;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends EdgeActivity {
    public static final String EXTRA_SESSION_EXPIRED = "session_expired";
    public static final String EXTRA_MESSAGE = "message";

    private AuthViewModel vm;
    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private TextView textError;
    private LinearProgressIndicator progress;
    private MaterialButton btnLogin;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEdgeContentView(R.layout.activity_login);
        vm = new ViewModelProvider(this).get(AuthViewModel.class);

        tilEmail = findViewById(R.id.tilEmail); tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail); etPassword = findViewById(R.id.etPassword);
        textError = findViewById(R.id.textError); progress = findViewById(R.id.progress); btnLogin = findViewById(R.id.btnLogin);

        if (getIntent().getBooleanExtra(EXTRA_SESSION_EXPIRED, false)) showError(getString(R.string.session_expired));
        String msg = getIntent().getStringExtra(EXTRA_MESSAGE);
        if (msg != null) Ui.toast(findViewById(R.id.root), msg);

        btnLogin.setOnClickListener(v -> submit());
        findViewById(R.id.btnForgot).setOnClickListener(v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
        findViewById(R.id.btnSignup).setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));
        findViewById(R.id.btnServer).setOnClickListener(v -> Ui.serverSettings(this, null));
        findViewById(R.id.btnTerms).setOnClickListener(v -> LegalActivity.open(this, LegalActivity.TERMS));
        findViewById(R.id.btnPrivacy).setOnClickListener(v -> LegalActivity.open(this, LegalActivity.PRIVACY));

        vm.login.observe(this, res -> {
            if (res == null) return;
            boolean loading = res.isLoading();
            progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
            btnLogin.setEnabled(!loading);
            if (res.isSuccess() && res.claim()) {
                App.from(this).prefs.setOnboardingDone(true);
                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            } else if (res.isError() && res.claim()) {
                if ("email_not_verified".equals(res.failure.code)) {
                    Intent i = new Intent(this, VerifyEmailActivity.class);
                    i.putExtra(VerifyEmailActivity.EXTRA_EMAIL, Ui.text(etEmail).trim());
                    startActivity(i);
                }
                if ("server_not_configured".equals(res.failure.code)) Ui.serverSettings(this, null);
                showError(res.failure.message);
            }
        });
    }

    private void showError(String m) {
        textError.setText(m);
        textError.setVisibility(View.VISIBLE);
    }

    private void submit() {
        textError.setVisibility(View.GONE);
        tilEmail.setError(null); tilPassword.setError(null);
        String email = Ui.text(etEmail).trim(), pw = Ui.text(etPassword);
        boolean ok = true;
        if (!Validators.isValidEmail(email)) { tilEmail.setError(getString(R.string.error_email)); ok = false; }
        if (pw.isEmpty()) { tilPassword.setError(getString(R.string.error_password_required)); ok = false; }
        if (!ok) return;
        vm.login(email, pw);
    }
}
