package com.asifqureshi.aivoicestudio.audio;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.asifqureshi.aivoicestudio.R;
import com.asifqureshi.aivoicestudio.utils.Background;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Saves audio to a user-chosen location through the Storage Access Framework (no storage permission needed;
 * the system picker asks before overwriting) and shares it with the Android share sheet.
 * Create it in Fragment.onCreate so the result launchers are registered in time.
 */
public final class AudioExporter {
    public interface Listener { void onSaved(String message, boolean success); }

    private final Fragment fragment;
    private final Listener listener;
    private final ActivityResultLauncher<String> mp3Launcher;
    private final ActivityResultLauncher<String> opusLauncher;
    private File pending;

    public AudioExporter(Fragment fragment, Listener listener) {
        this.fragment = fragment;
        this.listener = listener;
        mp3Launcher = fragment.registerForActivityResult(new ActivityResultContracts.CreateDocument("audio/mpeg"), this::onUri);
        opusLauncher = fragment.registerForActivityResult(new ActivityResultContracts.CreateDocument("audio/ogg"), this::onUri);
    }

    public void save(File source, String baseName) {
        pending = source;
        String name = baseName + "." + (source.getName().endsWith(".opus") ? "opus" : "mp3");
        (source.getName().endsWith(".opus") ? opusLauncher : mp3Launcher).launch(name);
    }

    private void onUri(Uri uri) {
        final Context ctx = fragment.getContext();
        if (uri == null || pending == null || ctx == null) return; // user cancelled the picker
        final File src = pending;
        final Context app = ctx.getApplicationContext();
        Background.run(() -> {
            boolean ok = false;
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = app.getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new java.io.IOException("No output stream");
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.flush();
                ok = true;
            } catch (Exception ignored) { }
            final boolean success = ok;
            new Handler(Looper.getMainLooper()).post(() -> listener.onSaved(
                    app.getString(success ? R.string.export_saved : R.string.export_failed), success));
        });
    }

    public void share(File file) {
        Context ctx = fragment.requireContext();
        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", file);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(file.getName().endsWith(".opus") ? "audio/ogg" : "audio/mpeg");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            fragment.startActivity(Intent.createChooser(send, ctx.getString(R.string.share_audio)));
        } catch (ActivityNotFoundException e) {
            listener.onSaved(ctx.getString(R.string.no_app_to_share), false);
        }
    }
}
