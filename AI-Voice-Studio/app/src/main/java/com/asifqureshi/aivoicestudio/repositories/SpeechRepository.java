package com.asifqureshi.aivoicestudio.repositories;

import com.asifqureshi.aivoicestudio.network.ApiClient;
import com.asifqureshi.aivoicestudio.network.ApiFailure;
import com.asifqureshi.aivoicestudio.network.ApiModels.*;
import com.asifqureshi.aivoicestudio.network.ApiResult;
import com.asifqureshi.aivoicestudio.utils.Format;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/** Speech generation, stored audio, history, usage and projects. All methods block. */
public final class SpeechRepository {
    private final ApiClient client;

    public SpeechRepository(ApiClient client) { this.client = client; }

    /** Lets the UI cancel an in-flight generation. */
    public static final class CancelHandle {
        private volatile Call<?> call;
        public void cancel() { Call<?> c = call; if (c != null) c.cancel(); }
    }

    public static final class TtsOutput {
        public File file;
        public String generationId, assetId, mime;
        public long bytes;
        public long usageRemaining = -1;
    }

    public ApiResult<TtsOutput> synthesize(TtsRequest req, File dir, CancelHandle handle) {
        Call<ResponseBody> call = client.api().tts(req);
        handle.call = call;
        try {
            Response<ResponseBody> r = call.execute();
            if (!r.isSuccessful()) {
                String body = r.errorBody() != null ? r.errorBody().string() : "";
                return ApiResult.failure(ApiFailure.fromErrorBody(r.code(), body, r.headers().get("Retry-After")));
            }
            ResponseBody b = r.body();
            if (b == null) return ApiResult.failure(new ApiFailure(502, "provider_empty_audio", "The server returned no audio.", 0, null));
            TtsOutput out = new TtsOutput();
            out.mime = b.contentType() != null ? b.contentType().toString() : "audio/mpeg";
            out.generationId = r.headers().get("X-Generation-Id");
            out.assetId = r.headers().get("X-Audio-Asset-Id");
            String rem = r.headers().get("X-Usage-Remaining");
            if (rem != null) { try { out.usageRemaining = Long.parseLong(rem); } catch (NumberFormatException ignored) { } }
            if (!dir.exists() && !dir.mkdirs()) {
                return ApiResult.failure(new ApiFailure(0, "storage_failed", "Could not create a temporary audio file.", 0, null));
            }
            out.file = new File(dir, "tts-" + (out.generationId != null ? out.generationId : System.currentTimeMillis()) + "." + Format.extensionFor(out.mime));
            copy(b.byteStream(), out.file);
            out.bytes = out.file.length();
            if (out.bytes == 0) return ApiResult.failure(new ApiFailure(502, "provider_empty_audio", "The server returned empty audio.", 0, null));
            return ApiResult.success(out);
        } catch (Exception e) {
            return ApiResult.failure(ApiFailure.fromThrowable(e));
        }
    }

    /** Downloads a stored audio asset into the cache (reusing an existing copy). */
    public ApiResult<File> downloadAudio(String assetId, String outputFormat, File dir) {
        String ext = outputFormat != null && outputFormat.startsWith("opus") ? "opus" : "mp3";
        File f = new File(dir, "asset-" + assetId + "." + ext);
        if (f.exists() && f.length() > 0) return ApiResult.success(f);
        try {
            if (!dir.exists() && !dir.mkdirs()) return ApiResult.failure(new ApiFailure(0, "storage_failed", "Could not create a temporary audio file.", 0, null));
            Response<ResponseBody> r = client.api().audio(assetId).execute();
            if (!r.isSuccessful()) {
                String body = r.errorBody() != null ? r.errorBody().string() : "";
                return ApiResult.failure(ApiFailure.fromErrorBody(r.code(), body, null));
            }
            copy(r.body().byteStream(), f);
            return ApiResult.success(f);
        } catch (Exception e) {
            f.delete();
            return ApiResult.failure(ApiFailure.fromThrowable(e));
        }
    }

    private static void copy(InputStream in, File dest) throws java.io.IOException {
        try (InputStream i = in; OutputStream o = new FileOutputStream(dest)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
        } catch (java.io.IOException e) {
            dest.delete();
            throw e;
        }
    }

    public ApiResult<Object> reportDuration(String assetId, long ms) { return ApiResult.execute(client.api().setDuration(assetId, new DurationRequest(ms))); }
    public ApiResult<Usage> usage() { return ApiResult.execute(client.api().usage()); }

    public ApiResult<GenerationsData> generations(String q, int limit, int offset) { return ApiResult.execute(client.api().generations(blankToNull(q), limit, offset)); }
    public ApiResult<GenerationData> generation(String id) { return ApiResult.execute(client.api().generation(id)); }
    public ApiResult<Object> deleteGeneration(String id) { return ApiResult.execute(client.api().deleteGeneration(id)); }

    public ApiResult<ProjectsData> projects(String q, Boolean favorite, int limit, int offset) { return ApiResult.execute(client.api().projects(blankToNull(q), favorite, limit, offset)); }
    public ApiResult<ProjectData> createProject(ProjectCreate body) { return ApiResult.execute(client.api().createProject(body)); }
    public ApiResult<ProjectData> project(String id) { return ApiResult.execute(client.api().project(id)); }
    public ApiResult<ProjectData> updateProject(String id, ProjectUpdate body) { return ApiResult.execute(client.api().updateProject(id, body)); }
    public ApiResult<ProjectData> duplicateProject(String id) { return ApiResult.execute(client.api().duplicateProject(id)); }
    public ApiResult<Object> deleteProject(String id) { return ApiResult.execute(client.api().deleteProject(id)); }

    private static String blankToNull(String s) { return s == null || s.trim().isEmpty() ? null : s.trim(); }
}
