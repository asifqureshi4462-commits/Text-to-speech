package com.asifqureshi.aivoicestudio.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.ui.auth.LoginActivity;
import com.asifqureshi.aivoicestudio.ui.common.Ui;
import com.asifqureshi.aivoicestudio.ui.settings.SettingsActivity;
import com.asifqureshi.aivoicestudio.utils.Format;
import com.asifqureshi.aivoicestudio.utils.Validators;
import com.asifqureshi.aivoicestudio.viewmodels.AccountViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ProfileFragment extends Fragment {
    private AccountViewModel vm;
    private TextView textAvatar, textName, textEmail, textStatus, textStats, textError;
    private SwipeRefreshLayout swipe;
    private String currentName = "";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        vm = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
        swipe = v.findViewById(R.id.swipe); textAvatar = v.findViewById(R.id.textAvatar); textName = v.findViewById(R.id.textName);
        textEmail = v.findViewById(R.id.textEmail); textStatus = v.findViewById(R.id.textAccountStatus);
        textStats = v.findViewById(R.id.textStats); textError = v.findViewById(R.id.textProfileError);

        swipe.setOnRefreshListener(() -> vm.refreshAll());
        v.findViewById(R.id.btnEditProfile).setOnClickListener(x -> Ui.prompt(requireContext(), R.string.edit_profile, R.string.full_name,
                currentName, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, name -> {
                    if (Validators.isValidName(name)) vm.rename(name); else Ui.toast(v, getString(R.string.error_name));
                }));
        v.findViewById(R.id.btnChangePassword).setOnClickListener(x -> showChangePassword());
        v.findViewById(R.id.btnSettings).setOnClickListener(x -> startActivity(new Intent(requireContext(), SettingsActivity.class)));
        v.findViewById(R.id.btnLogout).setOnClickListener(x -> Ui.confirm(requireContext(), R.string.logout, getString(R.string.logout_message), R.string.logout,
                () -> vm.logout(this::goToLogin)));
        v.findViewById(R.id.btnDeleteAccount).setOnClickListener(x -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_account).setMessage(R.string.delete_account_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.continue_label, (d, w) -> Ui.promptPassword(requireContext(), R.string.confirm_password_title, vm::deleteAccount))
                .show());

        vm.me.observe(getViewLifecycleOwner(), res -> {
            if (res == null) return;
            if (!res.isLoading()) swipe.setRefreshing(false);
            if (res.isSuccess()) {
                textError.setVisibility(View.GONE);
                currentName = res.data.user.name;
                textAvatar.setText(Format.initials(currentName));
                textName.setText(currentName);
                textEmail.setText(res.data.user.email);
                textStatus.setText(getString(R.string.account_status, getString(res.data.user.emailVerified ? R.string.verified : R.string.not_verified), res.data.user.plan));
                textStats.setText(getString(R.string.profile_stats, res.data.counts.projects, res.data.counts.generations, res.data.counts.favoriteVoices));
            } else if (res.isError()) {
                textError.setText(res.failure.message);
                textError.setVisibility(View.VISIBLE);
            }
        });
        vm.rename.observe(getViewLifecycleOwner(), res -> {
            if (res == null || res.isLoading() || !res.claim()) return;
            if (res.isSuccess()) { Ui.toast(v, getString(R.string.profile_updated)); vm.refreshAll(); }
            else Ui.failure(v, res.failure, null);
        });
        vm.changePassword.observe(getViewLifecycleOwner(), res -> {
            if (res == null || res.isLoading() || !res.claim()) return;
            if (res.isSuccess()) goToLogin(getString(R.string.password_changed_signin));
            else Ui.failure(v, res.failure, null);
        });
        vm.deleteAccount.observe(getViewLifecycleOwner(), res -> {
            if (res == null || res.isLoading() || !res.claim()) return;
            if (res.isSuccess()) goToLogin(getString(R.string.account_deleted));
            else Ui.failure(v, res.failure, null);
        });
    }

    private void goToLogin() { goToLogin(null); }

    private void goToLogin(@Nullable String message) {
        if (getActivity() == null) return;
        Intent i = new Intent(requireContext(), LoginActivity.class);
        if (message != null) i.putExtra(LoginActivity.EXTRA_MESSAGE, message);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        requireActivity().finish();
    }

    private void showChangePassword() {
        View content = getLayoutInflater().inflate(R.layout.dialog_change_password, null);
        final TextInputLayout tilCurrent = content.findViewById(R.id.tilCurrent), tilNew = content.findViewById(R.id.tilNew);
        final TextInputEditText etCurrent = content.findViewById(R.id.etCurrent), etNew = content.findViewById(R.id.etNew);
        final AlertDialog d = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.change_password).setView(content)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            tilCurrent.setError(null); tilNew.setError(null);
            String cur = Ui.text(etCurrent), next = Ui.text(etNew);
            if (cur.isEmpty()) { tilCurrent.setError(getString(R.string.error_password_required)); return; }
            if (!Validators.isStrongPassword(next)) { tilNew.setError(getString(R.string.error_password_weak)); return; }
            d.dismiss();
            vm.changePassword(cur, next);
        }));
        d.show();
    }
}
