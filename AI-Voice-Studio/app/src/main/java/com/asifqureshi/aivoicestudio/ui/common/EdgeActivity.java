package com.asifqureshi.aivoicestudio.ui.common;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.utils.InsetsUtil;

/** Base activity: edge-to-edge with correct system-bar and keyboard insets on the view with id "root". */
public abstract class EdgeActivity extends AppCompatActivity {
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
    }

    protected void setEdgeContentView(@LayoutRes int layout) {
        setContentView(layout);
        InsetsUtil.applySystemBars(findViewById(R.id.root), true, true);
    }
}
