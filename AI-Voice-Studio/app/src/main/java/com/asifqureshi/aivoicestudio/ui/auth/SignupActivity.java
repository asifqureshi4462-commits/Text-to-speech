package com.asifqureshi.aivoicestudio.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import com.asifqureshi.aivoicestudio.App;
import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.network.ApiModels.SignupData;
import com.asifqureshi.aivoicestudio.ui.common.EdgeActivity;
import com.asifqureshi.aivoicestudio.ui.common.Ui;
import com.asifqureshi.aivoicestudio.ui.main.MainActivity;
import com.asifqureshi.aivoicestudio.ui.settings.LegalActivity;
import com.asifqureshi.aivoicestudio.utils.Validators;
import com.asifqureshi.aivoicestudio.viewmodels.AuthViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SignupActivity extends EdgeActivity {
    private AuthViewModel vm;
    private TextInputLayout tilName, tilEmail, tilPassword, tilConfirm;
    private TextInputEditText etName, etEmail, etPassword, etConfirm;
    private CheckBox cbTerms;
    private TextView textError;
    private LinearProgressIndicator progress;
    private MaterialButton btnCreate;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEdgeContentView(R.layout.activity_signup);
        vm = new ViewModelProvider(this).get(AuthViewModel.class);
        tilName = findViewById(R.id.tilName); tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword); tilConfirm = findViewById(R.id.tilConfirm);
        etName = findViewById(R.id.etName); etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword); etConfirm = findViewById(R.id.etConfirm);
        cbTerms = findViewById(R.id.cbTerms); textError = findViewById(R.id.textError);
        progress = findViewById(R.id.progress); btnCreate = findViewById(R.id.btnCreate);

        btnCreate.setOnClickListener(v -> submit());
        findViewById(R.id.btnLogin).setOnClickListener(v -> finish());
        findViewById(R.id.btnTerms).setOnClickListener(v -> LegalActivity.open(this, LegalActivity.TERMS));
        findViewById(R.id.btnPrivacy).setOnClickListener(v -> LegalActivity.open(this, LegalActivity.PRIVACY));

        vm.signup.observe(this, res -> {
            if (res == null) return;
            progress.setVisibility(res.isLoading() ? View.VISIBLE : View.INVISIBLE);
            btnCreate.setEnabled(!res.isLoading());
            if (res.isSuccess() && res.claim()) {
                SignupData d = res.data;
                if (d.verification != null && d.verification.required) {
                    Intent i = new Intent(this, VerifyEmailActivity.class);
                    i.putExtra(VerifyEmailActivity.EXTRA_EMAIL, Ui.text(etEmail).trim());
                    i.putExtra(VerifyEmailActivity.EXTRA_DELIVERED, d.verification.emailDelivered);
                    startActivity(i);
                    finish();
                } else {
                    App.from(this).prefs.setOnboardingDone(true);
                    Intent i = new Intent(this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                }
            } else if (res.isError() && res.claim()) {
                if ("email_taken".equals(res.failure.code)) tilEmail.setError(res.failure.message);
                else if ("server_not_configured".equals(res.failure.code)) { Ui.serverSettings(this, null); showError(res.failure.message); }
                else showError(res.failure.message);
            }
        });
    }

    private void showError(String m) { textError.setText(m); textError.setVisibility(View.VISIBLE); }

    private void submit() {
        textError.setVisibility(View.GONE);
        tilName.setError(null); tilEmail.setError(null); tilPassword.setError(null); tilConfirm.setError(null);
        String name = Ui.text(etName).trim(), email = Ui.text(etEmail).trim(), pw = Ui.text(etPassword), conf = Ui.text(etConfirm);
        boolean ok = true;
        if (!Validators.isValidName(name)) { tilName.setError(getString(R.string.error_name)); ok = false; }
        if (!Validators.isValidEmail(email)) { tilEmail.setError(getString(R.string.error_email)); ok = false; }
        if (!Validators.isStrongPassword(pw)) { tilPassword.setError(getString(R.string.error_password_weak)); ok = false; }
        if (!pw.equals(conf)) { tilConfirm.setError(getString(R.string.error_password_mismatch)); ok = false; }
        if (!cbTerms.isChecked()) { showError(getString(R.string.error_terms)); ok = false; }
        if (!ok) return;
        vm.signup(name, email, pw);
    }
}
