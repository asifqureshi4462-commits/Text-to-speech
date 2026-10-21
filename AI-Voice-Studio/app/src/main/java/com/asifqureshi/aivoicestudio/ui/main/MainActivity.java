package com.asifqureshi.aivoicestudio.ui.main;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.asifqureshi.aivoicestudio.App;
import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.ui.auth.LoginActivity;
import com.asifqureshi.aivoicestudio.ui.home.HomeFragment;
import com.asifqureshi.aivoicestudio.ui.library.LibraryFragment;
import com.asifqureshi.aivoicestudio.ui.profile.ProfileFragment;
import com.asifqureshi.aivoicestudio.ui.tts.CreateFragment;
import com.asifqureshi.aivoicestudio.ui.voices.VoicesFragment;
import com.asifqureshi.aivoicestudio.utils.InsetsUtil;
import com.asifqureshi.aivoicestudio.viewmodels.SharedViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** Hosts the five tabs. Fragments are kept alive (show/hide) so each tab preserves its state. */
public class MainActivity extends AppCompatActivity {
    public static final int TAB_HOME = 0, TAB_CREATE = 1, TAB_VOICES = 2, TAB_LIBRARY = 3, TAB_PROFILE = 4;
    private static final String[] TAGS = {"home", "create", "voices", "library", "profile"};
    private static final int[] MENU_IDS = {R.id.nav_home, R.id.nav_create, R.id.nav_voices, R.id.nav_library, R.id.nav_profile};

    private BottomNavigationView nav;
    private int current = TAB_HOME;

    @Override protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        InsetsUtil.applySystemBars(findViewById(R.id.root), true, false);
        nav = findViewById(R.id.bottomNav);

        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState == null) {
            FragmentTransaction t = fm.beginTransaction().setReorderingAllowed(true);
            Fragment[] fs = {new HomeFragment(), new CreateFragment(), new VoicesFragment(), new LibraryFragment(), new ProfileFragment()};
            for (int i = 0; i < fs.length; i++) {
                t.add(R.id.fragmentContainer, fs[i], TAGS[i]);
                if (i != TAB_HOME) t.hide(fs[i]);
            }
            t.commit();
        } else {
            current = savedInstanceState.getInt("tab", TAB_HOME);
        }

        nav.setOnItemSelectedListener(item -> {
            for (int i = 0; i < MENU_IDS.length; i++) if (MENU_IDS[i] == item.getItemId()) { showTab(i); return true; }
            return false;
        });
        nav.setSelectedItemId(MENU_IDS[current]);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (current != TAB_HOME) nav.setSelectedItemId(MENU_IDS[TAB_HOME]);
                else finish();
            }
        });

        SharedViewModel shared = new ViewModelProvider(this).get(SharedViewModel.class);
        shared.loadModels(false);
        shared.loadFavorites();
        shared.tabRequest.observe(this, tab -> {
            if (tab != null) { nav.setSelectedItemId(MENU_IDS[tab]); shared.tabRequest.setValue(null); }
        });

        App.from(this).apiClient.sessionExpired.observe(this, expired -> {
            if (Boolean.TRUE.equals(expired)) {
                App.from(this).authRepository.clearLocalSession();
                Intent i = new Intent(this, LoginActivity.class);
                i.putExtra(LoginActivity.EXTRA_SESSION_EXPIRED, true);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                App.from(this).apiClient.sessionExpired.setValue(false);
                startActivity(i);
                finish();
            }
        });
    }

    private void showTab(int index) {
        if (index == current && getSupportFragmentManager().findFragmentByTag(TAGS[index]) != null
                && !getSupportFragmentManager().findFragmentByTag(TAGS[index]).isHidden()) return;
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction t = fm.beginTransaction().setReorderingAllowed(true);
        for (int i = 0; i < TAGS.length; i++) {
            Fragment f = fm.findFragmentByTag(TAGS[i]);
            if (f == null) continue;
            if (i == index) t.show(f); else t.hide(f);
        }
        t.commit();
        current = index;
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", current);
    }
}
