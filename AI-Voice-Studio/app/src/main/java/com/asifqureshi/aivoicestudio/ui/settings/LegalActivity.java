package com.asifqureshi.aivoicestudio.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.ui.common.EdgeActivity;
import com.google.android.material.appbar.MaterialToolbar;

/** Shows the bundled Terms, Privacy Policy or open-source licence notices. */
public class LegalActivity extends EdgeActivity {
    public static final String TERMS = "terms", PRIVACY = "privacy", LICENSES = "licenses";
    private static final String EXTRA_KIND = "kind";

    public static void open(Context c, String kind) {
        c.startActivity(new Intent(c, LegalActivity.class).putExtra(EXTRA_KIND, kind));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEdgeContentView(R.layout.activity_legal);
        String kind = getIntent().getStringExtra(EXTRA_KIND);
        int title, body;
        if (PRIVACY.equals(kind)) { title = R.string.privacy; body = R.string.privacy_body; }
        else if (LICENSES.equals(kind)) { title = R.string.open_source_licenses; body = R.string.licenses_body; }
        else { title = R.string.terms; body = R.string.terms_body; }
        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setTitle(title);
        tb.setNavigationIcon(R.drawable.ic_back);
        tb.setNavigationContentDescription(R.string.back);
        tb.setNavigationOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.textBody)).setText(body);
    }
}
