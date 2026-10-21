package com.asifqureshi.aivoicestudio.ui.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.network.ApiModels.Generation;

public class HistoryAdapter extends ListAdapter<Generation, HistoryAdapter.Holder> {
    public interface Actions {
        void onPlay(Generation g);
        void onDownload(Generation g);
        void onShare(Generation g);
        void onReuse(Generation g);
        void onDelete(Generation g);
    }

    private final Actions actions;

    public HistoryAdapter(Actions a) {
        super(new DiffUtil.ItemCallback<Generation>() {
            @Override public boolean areItemsTheSame(@NonNull Generation x, @NonNull Generation y) { return x.id.equals(y.id); }
            @Override public boolean areContentsTheSame(@NonNull Generation x, @NonNull Generation y) {
                return x.status.equals(y.status) && java.util.Objects.equals(x.durationMs, y.durationMs);
            }
        });
        actions = a;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_history, p, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int i) {
        Generation g = getItem(i);
        HistoryBinder.bind(h.itemView, g, true);
        h.itemView.findViewById(R.id.btnPlay).setOnClickListener(v -> actions.onPlay(g));
        h.itemView.findViewById(R.id.btnDownload).setOnClickListener(v -> actions.onDownload(g));
        h.itemView.findViewById(R.id.btnShare).setOnClickListener(v -> actions.onShare(g));
        h.itemView.findViewById(R.id.btnReuse).setOnClickListener(v -> actions.onReuse(g));
        h.itemView.findViewById(R.id.btnDelete).setOnClickListener(v -> actions.onDelete(g));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        Holder(View v) { super(v); }
    }
}
