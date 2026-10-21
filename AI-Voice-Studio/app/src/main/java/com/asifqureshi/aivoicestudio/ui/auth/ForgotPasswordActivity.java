package com.asifqureshi.aivoicestudio.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.ui.common.EdgeActivity;
import com.asifqureshi.aivoicestudio.ui.common.Ui;
import com.asifqureshi.aivoicestudio.utils.Validators;
import com.asifqureshi.aivoicestudio.viewmodels.AuthViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Two steps: request a reset code by email, then enter the code with a new password. */
public class ForgotPasswordActivity extends EdgeActivity {
    private AuthViewModel vm;
    private View stepRequest, stepReset;
    private TextInputLayout tilEmail, tilCode, tilPassword, tilConfirm;
    private TextInputEditText etEmail, etCode, etPassword, etConfirm;
    private TextView textError, textInfo;
    private LinearProgressIndicator progress;
    private MaterialButton btnSend, btnReset;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEdgeContentView(R.layout.activity_forgot);
        vm = new ViewModelProvider(this).get(AuthViewModel.class);
        stepRequest = findViewById(R.id.stepRequest); stepReset = findViewById(R.id.stepReset);
        tilEmail = findViewById(R.id.tilEmail); tilCode = findViewById(R.id.tilCode);
        tilPassword = findViewById(R.id.tilPassword); tilConfirm = findViewById(R.id.tilConfirm);
        etEmail = findViewById(R.id.etEmail); etCode = findViewById(R.id.etCode);
        etPassword = findViewById(R.id.etPassword); etConfirm = findViewById(R.id.etConfirm);
        textError = findViewById(R.id.textError); textInfo = findViewById(R.id.textInfo);
        progress = findViewById(R.id.progress); btnSend = findViewById(R.id.btnSend); btnReset = findViewById(R.id.btnReset);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> {
            textError.setVisibility(View.GONE); tilEmail.setError(null);
            String email = Ui.text(etEmail).trim();
            if (!Validators.isValidEmail(email)) { tilEmail.setError(getString(R.string.error_email)); return; }
            vm.forgot(email);
        });
        btnReset.setOnClickListener(v -> {
            textError.setVisibility(View.GONE);
            tilCode.setError(null); tilPassword.setError(null); tilConfirm.setError(null);
            String code = Ui.text(etCode).trim(), pw = Ui.text(etPassword), conf = Ui.text(etConfirm);
            boolean ok = true;
            if (code.length() < 10) { tilCode.setError(getString(R.string.error_code)); ok = false; }
            if (!Validators.isStrongPassword(pw)) { tilPassword.setError(getString(R.string.error_password_weak)); ok = false; }
            if (!pw.equals(conf)) { tilConfirm.setError(getString(R.string.error_password_mismatch)); ok = false; }
            if (ok) vm.reset(code, pw);
        });

        vm.forgot.observe(this, res -> {
            if (res == null) return;
            progress.setVisibility(res.isLoading() ? View.VISIBLE : View.INVISIBLE);
            btnSend.setEnabled(!res.isLoading());
            if (res.isSuccess() && res.claim()) {
                stepRequest.setVisibility(View.GONE);
                stepReset.setVisibility(View.VISIBLE);
                textInfo.setVisibility(View.VISIBLE);
                textInfo.setText(res.data != null && res.data.mailConfigured ? R.string.reset_code_sent : R.string.mail_not_configured);
            } else if (res.isError() && res.claim()) showError(res.failure.message);
        });
        vm.reset.observe(this, res -> {
            if (res == null) return;
            progress.setVisibility(res.isLoading() ? View.VISIBLE : View.INVISIBLE);
            btnReset.setEnabled(!res.isLoading());
            if (res.isSuccess() && res.claim()) {
                Intent i = new Intent(this, LoginActivity.class);
                i.putExtra(LoginActivity.EXTRA_MESSAGE, getString(R.string.password_reset_done));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            } else if (res.isError() && res.claim()) showError(res.failure.message);
        });
    }

    private void showError(String m) { textError.setText(m); textError.setVisibility(View.VISIBLE); }
}
