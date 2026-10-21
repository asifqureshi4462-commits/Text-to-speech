package com.asifqureshi.aivoicestudio.ui.library;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.asifqureshi.aivoicestudio.audio.AudioPlayerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;

/** Bottom sheet that plays a downloaded history item. The player releases itself when the sheet closes. */
public final class PlayerSheet {
    private PlayerSheet() {}

    public static void show(Context ctx, File file, String title) {
        BottomSheetDialog dialog = new BottomSheetDialog(ctx);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * ctx.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad * 2);
        TextView t = new TextView(ctx);
        t.setText(title);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setTextAppearance(com.asifqureshi.aivoicestudio.R.style.Text_Section);
        box.addView(t, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final AudioPlayerView player = new AudioPlayerView(ctx);
        box.addView(player, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(box);
        dialog.setOnDismissListener(d -> player.release());
        dialog.show();
        player.load(file, true);
    }
}
