package com.asifqureshi.aivoicestudio.network;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/** JSON shapes exchanged with the AI Voice Studio backend (field names match the API exactly). */
public final class ApiModels {
    private ApiModels() {}

    public static class Envelope<T> { public boolean ok; public T data; public ErrorBody error; }
    public static class ErrorBody { public String code; public String message; public JsonObject details; }

    // ---- auth / account ----
    public static class User { public String id, name, email, role, plan; public boolean emailVerified; public long createdAt; }
    public static class Session { public String accessToken, refreshToken; public int expiresIn; public User user; }
    public static class Verification { public boolean required; public boolean emailDelivered; public String reason; }
    public static class SignupData { public Verification verification; public Session session; public User user; }
    public static class Counts { public int projects, favoriteVoices, generations; }
    public static class MeData { public User user; public Counts counts; }
    public static class Requested { public boolean requested; public boolean mailConfigured; }

    public static class SignupRequest {
        public String name, email, password; public boolean acceptTerms;
        public SignupRequest(String n, String e, String p, boolean a) { name = n; email = e; password = p; acceptTerms = a; }
    }
    public static class LoginRequest {
        public String email, password;
        public LoginRequest(String e, String p) { email = e; password = p; }
    }
    public static class EmailRequest { public String email; public EmailRequest(String e) { email = e; } }
    public static class ResetRequest {
        public String token, password;
        public ResetRequest(String t, String p) { token = t; password = p; }
    }
    public static class TokenRequest { public String token; public TokenRequest(String t) { token = t; } }
    public static class RefreshRequest { public String refreshToken; public RefreshRequest(String t) { refreshToken = t; } }
    public static class ChangePasswordRequest {
        public String currentPassword, newPassword;
        public ChangePasswordRequest(String c, String n) { currentPassword = c; newPassword = n; }
    }
    public static class NameRequest { public String name; public NameRequest(String n) { name = n; } }
    public static class PasswordRequest { public String password; public PasswordRequest(String p) { password = p; } }

    // ---- voices / models ----
    public static class VoiceLanguage { public String language, locale, accent, modelId; }
    public static class Voice {
        public String voiceId, name, category, description, previewUrl;
        public Map<String, String> labels;
        public List<VoiceLanguage> languages;
    }
    public static class VoicesData { public List<Voice> voices; public boolean hasMore; public String nextPageToken; public Integer totalCount; }
    public static class ModelLanguage { public String id, name; }
    public static class TtsModel {
        public String modelId, name, description; public Integer maxCharacters;
        public boolean canUseStyle, canUseSpeakerBoost; public List<ModelLanguage> languages;
    }
    public static class ModelsData { public List<TtsModel> models; public List<String> outputFormats; public int maxTextChars; }

    // ---- speech ----
    public static class VoiceSettings { public Float stability, similarityBoost, style, speed; public Boolean useSpeakerBoost; }
    public static class TtsRequest {
        public String text, voiceId, modelId, outputFormat, languageCode, projectId;
        public VoiceSettings voiceSettings;
    }
    public static class Usage {
        public String plan, note; public long limitChars, usedChars, remainingChars, periodStart, periodEnd; public int generationsThisPeriod;
    }

    // ---- history / projects / favourites ----
    public static class Generation {
        public String id, projectId, provider, model, voiceId, textPreview, text, outputFormat, status, errorCode, errorMessage, audioAssetId;
        public int charCount; public Long durationMs, bytes; public JsonObject settings; public long createdAt; public Long completedAt;
    }
    public static class GenerationsData { public List<Generation> generations; public int total, limit, offset; }
    public static class GenerationData { public Generation generation; }

    public static class Project {
        public String id, name, type; public boolean isFavorite, isDraft; public long createdAt, updatedAt;
        public JsonObject content, settings; public Integer audioCount;
    }
    public static class ProjectsData { public List<Project> projects; public int total, limit, offset; }
    public static class ProjectData { public Project project; }
    public static class ProjectCreate { public String name, type; public JsonObject content, settings; public Boolean isDraft; }
    public static class ProjectUpdate { public String name; public Boolean isFavorite, isDraft; public JsonObject content, settings; }

    public static class FavoriteVoice { public String voiceId, name; public long createdAt; }
    public static class FavoritesData { public List<FavoriteVoice> voices; }
    public static class DurationRequest { public long durationMs; public DurationRequest(long d) { durationMs = d; } }
}
