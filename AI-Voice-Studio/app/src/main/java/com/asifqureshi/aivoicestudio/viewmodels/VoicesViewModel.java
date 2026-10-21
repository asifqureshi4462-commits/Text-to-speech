package com.asifqureshi.aivoicestudio.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.asifqureshi.aivoicestudio.App;
import com.asifqureshi.aivoicestudio.network.ApiFailure;
import com.asifqureshi.aivoicestudio.network.ApiModels.*;
import com.asifqureshi.aivoicestudio.network.ApiResult;
import com.asifqureshi.aivoicestudio.utils.Background;

import java.util.ArrayList;
import java.util.List;

/** Paged voice discovery (search, category filter, sort). */
public class VoicesViewModel extends AndroidViewModel {
    public static final class State {
        public List<Voice> items = new ArrayList<>();
        public boolean loading, hasMore;
        public ApiFailure failure;
        public Integer total;
    }

    private final App app;
    private String search = "", category = "", sort = "name", direction = "asc", nextToken;
    private int generation; // invalidates in-flight responses after a filter change
    public final MutableLiveData<State> state = new MutableLiveData<>(new State());

    public VoicesViewModel(@NonNull Application application) {
        super(application);
        app = App.from(application);
    }

    public void setFilters(String search, String category, String sort, String direction) {
        this.search = search == null ? "" : search;
        this.category = category == null ? "" : category;
        this.sort = sort; this.direction = direction;
        reload();
    }

    public String getSearch() { return search; }

    public void reload() {
        generation++;
        nextToken = null;
        State s = new State();
        s.loading = true;
        state.setValue(s);
        fetch(generation, new ArrayList<>());
    }

    public void loadMore() {
        State cur = state.getValue();
        if (cur == null || cur.loading || !cur.hasMore) return;
        State s = new State();
        s.items = cur.items; s.hasMore = cur.hasMore; s.total = cur.total; s.loading = true;
        state.setValue(s);
        fetch(generation, new ArrayList<>(cur.items));
    }

    private void fetch(final int gen, final List<Voice> existing) {
        final String q = search, c = category, so = sort, d = direction, tok = nextToken;
        Background.run(() -> {
            ApiResult<VoicesData> r = app.voiceRepository.voices(q, tok, c, so, d);
            if (gen != generation) return; // stale
            State s = new State();
            s.items = existing;
            if (r.isSuccess()) {
                if (r.data.voices != null) s.items.addAll(r.data.voices);
                s.hasMore = r.data.hasMore && r.data.nextPageToken != null;
                s.total = r.data.totalCount;
                nextToken = r.data.nextPageToken;
            } else {
                s.failure = r.failure;
            }
            state.postValue(s);
        });
    }
}
