package com.asifqureshi.aivoicestudio.repositories;

import com.asifqureshi.aivoicestudio.network.ApiClient;
import com.asifqureshi.aivoicestudio.network.ApiModels.*;
import com.asifqureshi.aivoicestudio.network.ApiResult;

/** Voices, models and favourite voices (all sourced from the backend / real provider). */
public final class VoiceRepository {
    public static final int PAGE_SIZE = 30;
    private final ApiClient client;

    public VoiceRepository(ApiClient client) { this.client = client; }

    public ApiResult<VoicesData> voices(String search, String pageToken, String category, String sort, String direction) {
        return ApiResult.execute(client.api().voices(emptyToNull(search), PAGE_SIZE, pageToken, emptyToNull(category), emptyToNull(sort), emptyToNull(direction)));
    }
    public ApiResult<ModelsData> models() { return ApiResult.execute(client.api().models()); }
    public ApiResult<FavoritesData> favorites() { return ApiResult.execute(client.api().favoriteVoices()); }
    public ApiResult<Object> addFavorite(String id, String name) { return ApiResult.execute(client.api().addFavoriteVoice(id, new NameRequest(name))); }
    public ApiResult<Object> removeFavorite(String id) { return ApiResult.execute(client.api().removeFavoriteVoice(id)); }

    private static String emptyToNull(String s) { return s == null || s.trim().isEmpty() ? null : s.trim(); }
}
