package com.asifqureshi.aivoicestudio.network;

import com.asifqureshi.aivoicestudio.network.ApiModels.*;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Streaming;

public interface ApiService {
    @POST("v1/auth/signup") Call<Envelope<SignupData>> signup(@Body SignupRequest body);
    @POST("v1/auth/login") Call<Envelope<Session>> login(@Body LoginRequest body);
    @POST("v1/auth/logout") Call<Envelope<Object>> logout(@Body RefreshRequest body);
    @POST("v1/auth/forgot-password") Call<Envelope<Requested>> forgotPassword(@Body EmailRequest body);
    @POST("v1/auth/reset-password") Call<Envelope<Object>> resetPassword(@Body ResetRequest body);
    @POST("v1/auth/verify-email") Call<Envelope<Object>> verifyEmail(@Body TokenRequest body);
    @POST("v1/auth/resend-verification") Call<Envelope<Requested>> resendVerification(@Body EmailRequest body);
    @POST("v1/auth/change-password") Call<Envelope<Object>> changePassword(@Body ChangePasswordRequest body);

    @GET("v1/me") Call<Envelope<MeData>> me();
    @PATCH("v1/me") Call<Envelope<MeData>> updateName(@Body NameRequest body);
    @HTTP(method = "DELETE", path = "v1/account", hasBody = true) Call<Envelope<Object>> deleteAccount(@Body PasswordRequest body);

    @GET("v1/voices")
    Call<Envelope<VoicesData>> voices(@Query("search") String search, @Query("pageSize") int pageSize,
                                      @Query("pageToken") String pageToken, @Query("category") String category,
                                      @Query("sort") String sort, @Query("sort_direction") String sortDirection);
    @GET("v1/models") Call<Envelope<ModelsData>> models();

    @Streaming @POST("v1/tts") Call<ResponseBody> tts(@Body TtsRequest body);
    @GET("v1/usage") Call<Envelope<Usage>> usage();

    @Streaming @GET("v1/audio/{id}") Call<ResponseBody> audio(@Path("id") String id);
    @PATCH("v1/audio/{id}") Call<Envelope<Object>> setDuration(@Path("id") String id, @Body DurationRequest body);

    @GET("v1/generations")
    Call<Envelope<GenerationsData>> generations(@Query("q") String q, @Query("limit") int limit, @Query("offset") int offset);
    @GET("v1/generations/{id}") Call<Envelope<GenerationData>> generation(@Path("id") String id);
    @DELETE("v1/generations/{id}") Call<Envelope<Object>> deleteGeneration(@Path("id") String id);

    @GET("v1/projects")
    Call<Envelope<ProjectsData>> projects(@Query("q") String q, @Query("favorite") Boolean favorite,
                                          @Query("limit") int limit, @Query("offset") int offset);
    @POST("v1/projects") Call<Envelope<ProjectData>> createProject(@Body ProjectCreate body);
    @GET("v1/projects/{id}") Call<Envelope<ProjectData>> project(@Path("id") String id);
    @PATCH("v1/projects/{id}") Call<Envelope<ProjectData>> updateProject(@Path("id") String id, @Body ProjectUpdate body);
    @POST("v1/projects/{id}/duplicate") Call<Envelope<ProjectData>> duplicateProject(@Path("id") String id);
    @DELETE("v1/projects/{id}") Call<Envelope<Object>> deleteProject(@Path("id") String id);

    @GET("v1/favorites/voices") Call<Envelope<FavoritesData>> favoriteVoices();
    @PUT("v1/favorites/voices/{id}") Call<Envelope<Object>> addFavoriteVoice(@Path("id") String id, @Body NameRequest body);
    @DELETE("v1/favorites/voices/{id}") Call<Envelope<Object>> removeFavoriteVoice(@Path("id") String id);
}
