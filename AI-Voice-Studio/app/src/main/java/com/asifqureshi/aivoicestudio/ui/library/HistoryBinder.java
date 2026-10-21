package com.asifqureshi.aivoicestudio.ui.library;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.network.ApiModels.Generation;
import com.asifqureshi.aivoicestudio.utils.Format;

/** Binds a Generation to the item_history layout (shared by Home and Library). */
public final class HistoryBinder {
    private HistoryBinder() {}

    public static void bind(View row, Generation g, boolean showActions) {
        TextView preview = row.findViewById(R.id.textPreview);
        TextView status = row.findViewById(R.id.textStatus);
        TextView meta = row.findViewById(R.id.textMeta);
        TextView error = row.findViewById(R.id.textError);
        View actions = row.findViewById(R.id.rowActions);
        Context ctx = row.getContext();

        preview.setText(g.textPreview == null || g.textPreview.isEmpty() ? ctx.getString(R.string.no_text_preview) : g.textPreview);
        int statusRes = "completed".equals(g.status) ? R.string.status_completed : "failed".equals(g.status) ? R.string.status_failed : R.string.status_processing;
        status.setText(statusRes);
        StringBuilder sb = new StringBuilder();
        sb.append(g.model).append(" · ").append(ctx.getString(R.string.chars_count, g.charCount));
        if (g.durationMs != null) sb.append(" · ").append(Format.duration(g.durationMs));
        if (g.bytes != null) sb.append(" · ").append(Format.fileSize(g.bytes));
        sb.append("\n").append(Format.dateTime(g.createdAt));
        meta.setText(sb.toString());
        boolean failed = "failed".equals(g.status);
        error.setVisibility(failed ? View.VISIBLE : View.GONE);
        if (failed) error.setText(g.errorMessage == null ? "" : g.errorMessage);
        actions.setVisibility(showActions ? View.VISIBLE : View.GONE);
        if (showActions) {
            boolean playable = "completed".equals(g.status) && g.audioAssetId != null;
            row.findViewById(R.id.btnPlay).setVisibility(playable ? View.VISIBLE : View.GONE);
            row.findViewById(R.id.btnDownload).setVisibility(playable ? View.VISIBLE : View.GONE);
            row.findViewById(R.id.btnShare).setVisibility(playable ? View.VISIBLE : View.GONE);
        }
    }
}
