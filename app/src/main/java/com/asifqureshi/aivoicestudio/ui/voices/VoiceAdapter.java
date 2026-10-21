package com.asifqureshi.aivoicestudio.ui.voices;

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
import com.asifqureshi.aivoicestudio.network.ApiModels.Voice;
import com.asifqureshi.aivoicestudio.network.ApiModels.VoiceLanguage;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class VoiceAdapter extends ListAdapter<Voice, VoiceAdapter.Holder> {
    public interface Actions {
        void onUse(Voice v);
        void onToggleFavorite(Voice v);
        void onPreview(Voice v);
    }

    private final Actions actions;
    private Set<String> favorites = new HashSet<>();
    private String selectedId, previewingId;

    public VoiceAdapter(Actions actions) {
        super(new DiffUtil.ItemCallback<Voice>() {
            @Override public boolean areItemsTheSame(@NonNull Voice a, @NonNull Voice b) { return a.voiceId.equals(b.voiceId); }
            @Override public boolean areContentsTheSame(@NonNull Voice a, @NonNull Voice b) { return a.name != null && a.name.equals(b.name); }
        });
        this.actions = actions;
    }

    public void setState(Set<String> favs, String selected, String previewing) {
        favorites = favs == null ? new HashSet<>() : favs;
        selectedId = selected;
        previewingId = previewing;
        notifyItemRangeChanged(0, getItemCount()); // cheap: only bound (visible) rows are rebound
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_voice, p, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        Voice v = getItem(position);
        Context c = h.itemView.getContext();
        h.name.setText(v.name);
        StringBuilder meta = new StringBuilder();
        if (v.category != null) meta.append(v.category);
        if (v.labels != null) for (String key : new String[]{"accent", "gender", "age", "use_case"}) {
            String val = v.labels.get(key);
            if (val != null && !val.isEmpty()) meta.append(meta.length() > 0 ? " · " : "").append(val);
        }
        h.meta.setText(meta);
        h.meta.setVisibility(meta.length() == 0 ? View.GONE : View.VISIBLE);
        h.desc.setText(v.description);
        h.desc.setVisibility(v.description == null || v.description.isEmpty() ? View.GONE : View.VISIBLE);
        Set<String> langs = new LinkedHashSet<>();
        if (v.languages != null) for (VoiceLanguage l : v.languages) if (l.language != null) langs.add(l.language);
        h.langs.setText(langs.isEmpty() ? "" : c.getString(R.string.languages_label, String.join(", ", langs)));
        h.langs.setVisibility(langs.isEmpty() ? View.GONE : View.VISIBLE);

        boolean selected = v.voiceId.equals(selectedId);
        h.card.setStrokeColor(c.getColor(selected ? R.color.brand_cyan : R.color.app_outline));
        h.card.setStrokeWidth((int) (c.getResources().getDisplayMetrics().density * (selected ? 2 : 1)));
        h.use.setText(selected ? R.string.selected : R.string.use_voice);
        boolean fav = favorites.contains(v.voiceId);
        h.fav.setImageResource(fav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        h.fav.setContentDescription(c.getString(fav ? R.string.remove_favourite : R.string.add_favourite));
        boolean hasPreview = v.previewUrl != null && !v.previewUrl.isEmpty();
        h.preview.setVisibility(hasPreview ? View.VISIBLE : View.GONE);
        boolean previewing = v.voiceId.equals(previewingId);
        h.preview.setImageResource(previewing ? R.drawable.ic_stop : R.drawable.ic_play);
        h.use.setOnClickListener(x -> actions.onUse(v));
        h.fav.setOnClickListener(x -> actions.onToggleFavorite(v));
        h.preview.setOnClickListener(x -> actions.onPreview(v));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card; final TextView name, meta, desc, langs; final ImageButton preview, fav; final MaterialButton use;
        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.cardVoice); name = v.findViewById(R.id.textVoiceName); meta = v.findViewById(R.id.textVoiceMeta);
            desc = v.findViewById(R.id.textVoiceDescription); langs = v.findViewById(R.id.textVoiceLanguages);
            preview = v.findViewById(R.id.btnPreview); fav = v.findViewById(R.id.btnFavorite); use = v.findViewById(R.id.btnUseVoice);
        }
    }
}
