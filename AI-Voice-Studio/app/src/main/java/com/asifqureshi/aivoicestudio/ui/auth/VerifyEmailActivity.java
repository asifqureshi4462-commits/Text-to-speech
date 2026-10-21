package com.asifqureshi.aivoicestudio.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.ui.common.EdgeActivity;
import com.asifqureshi.aivoicestudio.ui.common.Ui;
import com.asifqureshi.aivoicestudio.viewmodels.AuthViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Verification pending: enter the code (or open the emailed link), or request a new code. */
public class VerifyEmailActivity extends EdgeActivity {
    public static final String EXTRA_EMAIL = "email";
    public static final String EXTRA_DELIVERED = "delivered";

    private AuthViewModel vm;
    private String email;
    private TextInputLayout tilCode;
    private TextInputEditText etCode;
    private TextView textError, textInfo;
    private LinearProgressIndicator progress;
    private MaterialButton btnVerify, btnResend;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEdgeContentView(R.layout.activity_verify);
        vm = new ViewModelProvider(this).get(AuthViewModel.class);
        email = getIntent().getStringExtra(EXTRA_EMAIL);
        tilCode = findViewById(R.id.tilCode); etCode = findViewById(R.id.etCode);
        textError = findViewById(R.id.textError); textInfo = findViewById(R.id.textInfo);
        progress = findViewById(R.id.progress); btnVerify = findViewById(R.id.btnVerify); btnResend = findViewById(R.id.btnResend);

        boolean delivered = getIntent().getBooleanExtra(EXTRA_DELIVERED, true);
        textInfo.setVisibility(View.VISIBLE);
        textInfo.setText(delivered ? getString(R.string.verify_sent_to, email == null ? "" : email) : getString(R.string.mail_not_configured));

        findViewById(R.id.btnBack).setOnClickListener(v -> goLogin(null));
        btnVerify.setOnClickListener(v -> {
            tilCode.setError(null); textError.setVisibility(View.GONE);
            String code = Ui.text(etCode).trim();
            if (code.length() < 10) { tilCode.setError(getString(R.string.error_code)); return; }
            vm.verify(code);
        });
        btnResend.setOnClickListener(v -> { if (email != null && !email.isEmpty()) vm.resend(email); });

        vm.verify.observe(this, res -> {
            if (res == null) return;
            progress.setVisibility(res.isLoading() ? View.VISIBLE : View.INVISIBLE);
            btnVerify.setEnabled(!res.isLoading());
            if (res.isSuccess() && res.claim()) goLogin(getString(R.string.email_verified));
            else if (res.isError() && res.claim()) { textError.setText(res.failure.message); textError.setVisibility(View.VISIBLE); }
        });
        vm.resend.observe(this, res -> {
            if (res == null) return;
            btnResend.setEnabled(!res.isLoading());
            if (res.isSuccess() && res.claim()) Ui.toast(findViewById(R.id.root), getString(res.data != null && res.data.mailConfigured ? R.string.verification_resent : R.string.mail_not_configured));
            else if (res.isError() && res.claim()) Ui.toast(findViewById(R.id.root), res.failure.message);
        });
    }

    private void goLogin(String message) {
        Intent i = new Intent(this, LoginActivity.class);
        if (message != null) i.putExtra(LoginActivity.EXTRA_MESSAGE, message);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
