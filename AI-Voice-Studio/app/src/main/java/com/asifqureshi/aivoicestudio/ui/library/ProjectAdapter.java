package com.asifqureshi.aivoicestudio.ui.library;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.network.ApiModels.Project;
import com.asifqureshi.aivoicestudio.utils.Format;

public class ProjectAdapter extends ListAdapter<Project, ProjectAdapter.Holder> {
    public interface Actions {
        void onOpen(Project p);
        void onToggleFavorite(Project p);
        void onMore(Project p, View anchor);
    }

    private final Actions actions;

    public ProjectAdapter(Actions a) {
        super(new DiffUtil.ItemCallback<Project>() {
            @Override public boolean areItemsTheSame(@NonNull Project x, @NonNull Project y) { return x.id.equals(y.id); }
            @Override public boolean areContentsTheSame(@NonNull Project x, @NonNull Project y) {
                return x.name.equals(y.name) && x.updatedAt == y.updatedAt && x.isFavorite == y.isFavorite;
            }
        });
        actions = a;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_project, p, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int i) {
        Project p = getItem(i);
        Context c = h.itemView.getContext();
        h.name.setText(p.name);
        String audio = p.audioCount != null && p.audioCount > 0 ? " · " + c.getString(R.string.audio_files_count, p.audioCount) : "";
        h.meta.setText(c.getString(p.isDraft ? R.string.project_meta_draft : R.string.project_meta, p.type, Format.dateTime(p.updatedAt)) + audio);
        h.fav.setImageResource(p.isFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        h.fav.setContentDescription(c.getString(p.isFavorite ? R.string.remove_favourite : R.string.add_favourite));
        h.fav.setOnClickListener(v -> actions.onToggleFavorite(p));
        h.open.setOnClickListener(v -> actions.onOpen(p));
        h.more.setOnClickListener(v -> actions.onMore(p, v));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name, meta; final ImageButton fav, more; final View open;
        Holder(View v) {
            super(v);
            name = v.findViewById(R.id.textProjectName); meta = v.findViewById(R.id.textProjectMeta);
            fav = v.findViewById(R.id.btnProjectFavorite); more = v.findViewById(R.id.btnProjectMore); open = v.findViewById(R.id.btnProjectOpen);
        }
    }
}
