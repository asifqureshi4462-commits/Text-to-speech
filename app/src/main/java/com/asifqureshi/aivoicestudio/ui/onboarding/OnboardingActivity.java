package com.asifqureshi.aivoicestudio.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.asifqureshi.aivoicestudio.App;
import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.ui.auth.LoginActivity;
import com.asifqureshi.aivoicestudio.ui.common.EdgeActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class OnboardingActivity extends EdgeActivity {
    private static final int[] ART = {R.drawable.art_onboard_1, R.drawable.art_onboard_2, R.drawable.art_onboard_3};
    private static final int[] TITLE = {R.string.onboard1_title, R.string.onboard2_title, R.string.onboard3_title};
    private static final int[] BODY = {R.string.onboard1_body, R.string.onboard2_body, R.string.onboard3_body};

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEdgeContentView(R.layout.activity_onboarding);
        final ViewPager2 pager = findViewById(R.id.pager);
        final MaterialButton next = findViewById(R.id.btnNext);
        final View skip = findViewById(R.id.btnSkip);
        pager.setAdapter(new PageAdapter());
        new TabLayoutMediator((TabLayout) findViewById(R.id.dots), pager, (tab, position) -> { }).attach();

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                boolean last = position == ART.length - 1;
                next.setText(last ? R.string.get_started : R.string.next);
                skip.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
            }
        });
        next.setOnClickListener(v -> {
            if (pager.getCurrentItem() < ART.length - 1) pager.setCurrentItem(pager.getCurrentItem() + 1, true);
            else finishOnboarding();
        });
        skip.setOnClickListener(v -> finishOnboarding());
    }

    private void finishOnboarding() {
        App.from(this).prefs.setOnboardingDone(true); // never shown again
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private static final class PageAdapter extends RecyclerView.Adapter<PageAdapter.Holder> {
        static final class Holder extends RecyclerView.ViewHolder {
            final ImageView image; final TextView title, body;
            Holder(View v) { super(v); image = v.findViewById(R.id.obImage); title = v.findViewById(R.id.obTitle); body = v.findViewById(R.id.obBody); }
        }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_onboarding, p, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder h, int i) {
            h.image.setImageResource(ART[i]); h.title.setText(TITLE[i]); h.body.setText(BODY[i]);
        }
        @Override public int getItemCount() { return ART.length; }
    }
}
