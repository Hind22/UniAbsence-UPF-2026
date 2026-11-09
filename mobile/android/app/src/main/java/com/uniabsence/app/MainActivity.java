package com.uniabsence.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import android.content.pm.ApplicationInfo;
import android.webkit.WebSettings;

import androidx.core.content.FileProvider;

import com.getcapacitor.BridgeActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends BridgeActivity {
    private static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getBridge() != null) {
            WebView webView = getBridge().getWebView();
            webView.addJavascriptInterface(new JustificationFileBridge(this, webView), "UniAbsenceFile");
            webView.reload();
        }
    }

    @Override
    protected void load() {
        super.load();
        boolean isDebuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (isDebuggable && getBridge() != null) {
            getBridge().getWebView().getSettings()
                .setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
    }

    private static final class JustificationFileBridge {
        private final Activity activity;
        private final WebView webView;

        private JustificationFileBridge(Activity activity, WebView webView) {
            this.activity = activity;
            this.webView = webView;
        }

        @JavascriptInterface
        public String openFile(String fileName, String contentType, String base64) {
            String pageUrl = currentWebViewUrl();
            if (pageUrl == null) {
                return "Impossible de vérifier l’origine de la page Android.";
            }
            Uri pageUri = Uri.parse(pageUrl);
            if (!"https".equals(pageUri.getScheme()) || !"localhost".equals(pageUri.getHost())) {
                return "Cette action n’est autorisée que depuis l’application UniAbsence.";
            }

            String extension = supportedExtension(fileName, contentType);
            if (extension == null || base64 == null || base64.isBlank()) {
                return "Le justificatif reçu n’est pas un PDF, JPG ou PNG valide.";
            }
            if (base64.length() > ((MAX_FILE_SIZE_BYTES + 2L) / 3L) * 4L + 16L) {
                return "La taille maximale d’un justificatif est de 10 Mo.";
            }

            final byte[] contents;
            try {
                contents = Base64.decode(base64, Base64.DEFAULT);
            } catch (IllegalArgumentException exception) {
                return "Le justificatif téléchargé est illisible.";
            }
            if (contents.length == 0 || contents.length > MAX_FILE_SIZE_BYTES) {
                return "La taille du justificatif téléchargé est invalide.";
            }

            String safeName = sanitizeFileName(fileName, extension);
            File directory = new File(activity.getCacheDir(), "justifications");
            File file = new File(directory, UUID.randomUUID() + "-" + safeName);
            try {
                if (!directory.exists() && !directory.mkdirs()) {
                    return "Impossible de créer le dossier temporaire Android.";
                }
                try (FileOutputStream output = new FileOutputStream(file)) {
                    output.write(contents);
                }
            } catch (IOException exception) {
                return "Impossible d’enregistrer temporairement le justificatif sur Android.";
            }

            Uri fileUri;
            try {
                fileUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    file
                );
            } catch (IllegalArgumentException exception) {
                if (!file.delete()) {
                    file.deleteOnExit();
                }
                return "Impossible de préparer le justificatif pour son ouverture sur Android.";
            }
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(fileUri, contentType);
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(viewIntent, "Ouvrir le justificatif");

            AtomicReference<String> launchError = new AtomicReference<>();
            CountDownLatch launched = new CountDownLatch(1);
            activity.runOnUiThread(() -> {
                try {
                    activity.startActivity(chooser);
                } catch (ActivityNotFoundException exception) {
                    launchError.set("Aucune application Android ne peut ouvrir ce type de justificatif.");
                    if (!file.delete()) {
                        file.deleteOnExit();
                    }
                } finally {
                    launched.countDown();
                }
            });
            try {
                if (!launched.await(5, TimeUnit.SECONDS)) {
                    return "Android n’a pas pu ouvrir le justificatif à temps.";
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return "L’ouverture du justificatif a été interrompue.";
            }
            return launchError.get() == null ? "opened" : launchError.get();
        }

        private String currentWebViewUrl() {
            AtomicReference<String> pageUrl = new AtomicReference<>();
            CountDownLatch checked = new CountDownLatch(1);
            activity.runOnUiThread(() -> {
                pageUrl.set(webView.getUrl());
                checked.countDown();
            });
            try {
                return checked.await(5, TimeUnit.SECONDS) ? pageUrl.get() : null;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        private String supportedExtension(String fileName, String contentType) {
            if (fileName == null || contentType == null) {
                return null;
            }
            String lowerName = fileName.toLowerCase(java.util.Locale.ROOT);
            if (lowerName.endsWith(".pdf") && "application/pdf".equals(contentType)) {
                return "pdf";
            }
            if ((lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg"))
                    && "image/jpeg".equals(contentType)) {
                return lowerName.endsWith(".jpeg") ? "jpeg" : "jpg";
            }
            if (lowerName.endsWith(".png") && "image/png".equals(contentType)) {
                return "png";
            }
            return null;
        }

        private String sanitizeFileName(String fileName, String extension) {
            String safeName = fileName.replace('\\', '/');
            safeName = safeName.substring(safeName.lastIndexOf('/') + 1);
            safeName = safeName.replaceAll("[^A-Za-z0-9._-]", "_");
            if (safeName.isBlank() || !safeName.toLowerCase(java.util.Locale.ROOT).endsWith("." + extension)) {
                return "justificatif." + extension;
            }
            return safeName;
        }
    }
}
