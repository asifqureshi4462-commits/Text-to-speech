package com.asifqureshi.aivoicestudio.ui.common;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.asifqureshi.aivoicestudio.App;
import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.network.ApiFailure;
import com.asifqureshi.aivoicestudio.utils.Validators;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.function.Consumer;

/** Small shared UI helpers (snackbars, dialogs). */
public final class Ui {
    private Ui() {}

    public static void toast(View anchor, String message) {
        Snackbar.make(anchor, message, Snackbar.LENGTH_LONG).show();
    }

    public static void failure(View anchor, ApiFailure f, Runnable retry) {
        Snackbar s = Snackbar.make(anchor, f.message, Snackbar.LENGTH_INDEFINITE);
        s.setAction(retry != null && f.isRetryable() ? R.string.retry : R.string.dismiss, v -> {
            if (retry != null && f.isRetryable()) retry.run();
        });
        s.show();
    }

    public static void confirm(Context c, int titleRes, String message, int positiveRes, Runnable onYes) {
        new MaterialAlertDialogBuilder(c).setTitle(titleRes).setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(positiveRes, (d, w) -> onYes.run()).show();
    }

    /** Single-line text prompt (rename, etc.). */
    public static void prompt(Context c, int titleRes, int hintRes, String initial, int inputType, Consumer<String> onOk) {
        TextInputLayout til = new TextInputLayout(c);
        til.setHint(c.getString(hintRes));
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setInputType(inputType);
        et.setSingleLine(true);
        if (initial != null) et.setText(initial);
        til.addView(et);
        FrameLayout box = new FrameLayout(c);
        int pad = (int) (20 * c.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(til);
        new MaterialAlertDialogBuilder(c).setTitle(titleRes).setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String v = et.getText() == null ? "" : et.getText().toString().trim();
                    if (!v.isEmpty()) onOk.accept(v);
                }).show();
    }

    public static void promptPassword(Context c, int titleRes, Consumer<String> onOk) {
        prompt(c, titleRes, R.string.password, null, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, onOk);
    }

    /** Server address dialog (HTTPS required except for local development hosts). */
    public static void serverSettings(Activity a, Runnable onSaved) {
        App app = App.from(a);
        TextInputLayout til = new TextInputLayout(a);
        til.setHint(a.getString(R.string.server_url_hint));
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        et.setSingleLine(true);
        et.setText(app.prefs.getServerUrl());
        til.addView(et);
        FrameLayout box = new FrameLayout(a);
        int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(til);
        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(a)
                .setTitle(R.string.server_settings).setMessage(R.string.server_settings_help).setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null).create();
        d.setOnShowListener(x -> d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String url = Validators.normalizeServerUrl(et.getText() == null ? "" : et.getText().toString());
            if (url == null) { til.setError(a.getString(R.string.server_url_invalid)); return; }
            app.prefs.setServerUrl(url);
            app.apiClient.rebuild();
            d.dismiss();
            if (onSaved != null) onSaved.run();
        }));
        d.show();
    }

    public static String text(EditText e) { return e.getText() == null ? "" : e.getText().toString(); }
}
