package com.asifqureshi.aivoicestudio.repositories;

import com.asifqureshi.aivoicestudio.data.Prefs;
import com.asifqureshi.aivoicestudio.network.ApiClient;
import com.asifqureshi.aivoicestudio.network.ApiModels.*;
import com.asifqureshi.aivoicestudio.network.ApiResult;

/** Voices, models, providers and favourite voices (all sourced from the backend / the selected real provider). */
public final class VoiceRepository {
    public static final int PAGE_SIZE = 30;
    private final ApiClient client;
    private final Prefs prefs;

    public VoiceRepository(ApiClient client, Prefs prefs) {
        this.client = client;
        this.prefs = prefs;
    }

    /** null means "let the server use its default provider". */
    private String provider() { return prefs.getProvider(); }

    public ApiResult<ProvidersData> providers() { return ApiResult.execute(client.api().providers()); }

    public ApiResult<VoicesData> voices(String search, String pageToken, String category, String sort, String direction) {
        return ApiResult.execute(client.api().voices(emptyToNull(search), PAGE_SIZE, pageToken, emptyToNull(category), emptyToNull(sort), emptyToNull(direction), provider()));
    }
    public ApiResult<ModelsData> models() { return ApiResult.execute(client.api().models(provider())); }
    public ApiResult<FavoritesData> favorites() { return ApiResult.execute(client.api().favoriteVoices(provider())); }
    public ApiResult<Object> addFavorite(String id, String name) { return ApiResult.execute(client.api().addFavoriteVoice(id, new FavoriteRequest(name, provider()))); }
    public ApiResult<Object> removeFavorite(String id) { return ApiResult.execute(client.api().removeFavoriteVoice(id)); }

    private static String emptyToNull(String s) { return s == null || s.trim().isEmpty() ? null : s.trim(); }
}
