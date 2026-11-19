package com.qiuzh.deepseekchat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * DS Chat：把 DeepSeek 官方开源 chat-demo（静态前端）装进 WebView 壳。
 *
 * 资源通过 https://appassets.androidplatform.net/ 虚拟域名提供（不用 file://），
 * 这样 localStorage / IndexedDB / getUserMedia / CORS 都按正常 https 站点工作。
 */
public class MainActivity extends Activity {

    private static final String ORIGIN_HOST = "appassets.androidplatform.net";
    private static final String ASSET_ROOT = "www";

    private static final int REQ_FILE_CHOOSER = 1001;
    private static final int REQ_MIC_PERMISSION = 1002;
    private static final int REQ_CREATE_DOCUMENT = 1003;
    private static final int REQ_CAMERA = 1004;
    private static final int REQ_MEDIA_PERMISSION = 1005;

    private WebView webView;
    private ValueCallback<Uri[]> pendingFileCallback;
    private PermissionRequest pendingPermissionRequest;
    private WebChromeClient.FileChooserParams pendingChooserParams;
    private Uri pendingCameraUri;

    private byte[] pendingDocumentBytes;
    private long lastBackPress;
    private final java.util.concurrent.ExecutorService fileCopyExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        webView.setBackgroundColor(isNightMode() ? 0xFF17181A : 0xFFFFFFFF);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setAllowFileAccess(true); // 文件选择器结果会先拷到缓存，再以 file:// 交给网页
        settings.setAllowContentAccess(true); // 文件选择器返回的是 content:// URI，必须允许读取
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setTextZoom(100); // 不跟随系统字体缩放，避免布局错乱
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        webView.addJavascriptInterface(new NativeBridge(), "DSNative");
        webView.setWebViewClient(new ChatWebViewClient());
        webView.setWebChromeClient(new ChatChromeClient());
        webView.setDownloadListener(new BlobAwareDownloadListener());

        // 调试包允许 chrome://inspect 远程调试 WebView
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        if (savedInstanceState == null) {
            clearPickedFiles();
            webView.loadUrl("https://" + ORIGIN_HOST + "/index.html");
        }
    }

    private boolean isNightMode() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    // ---------------------------------------------------------------- WebViewClient

    private class ChatWebViewClient extends WebViewClient {

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (uri != null && ORIGIN_HOST.equals(uri.getHost())) {
                String path = uri.getPath();
                if (path != null && path.startsWith("/__thumb")) {
                    return serveThumb(uri.getQueryParameter("id"));
                }
                if (path != null && path.startsWith("/__picked/")) {
                    return servePicked(path.substring("/__picked/".length()));
                }
                return serveAsset(uri);
            }
            return null;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (uri == null) {
                return false;
            }
            if (ORIGIN_HOST.equals(uri.getHost())) {
                return false; // 站内导航交给 WebView
            }
            openExternally(uri);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            view.animate().alpha(1f).setDuration(180).start();
        }
    }

    private void openExternally(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, "没有可打开该链接的应用", Toast.LENGTH_SHORT).show();
        }
    }

    /** 把 https://appassets.androidplatform.net/xxx 映射到 assets/www/xxx。 */
    private WebResourceResponse serveAsset(Uri uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            path = "/index.html";
        }
        if (path.contains("..")) {
            return notFound();
        }

        String assetPath = ASSET_ROOT + path;
        try {
            InputStream in = getAssets().open(assetPath);
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", path.equals("/index.html")
                    ? "no-cache"
                    : "public, max-age=31536000, immutable");
            WebResourceResponse response = new WebResourceResponse(
                    mimeTypeOf(path), encodingOf(path), 200, "OK", headers, in);
            return response;
        } catch (IOException e) {
            // 单页应用兜底：未知的无扩展名路径回落到 index.html
            if (!path.contains(".")) {
                try {
                    return new WebResourceResponse("text/html", "utf-8",
                            getAssets().open(ASSET_ROOT + "/index.html"));
                } catch (IOException ignored) {
                    // fallthrough
                }
            }
            return notFound();
        }
    }

    private WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8",
                new ByteArrayInputStream("Not found".getBytes()));
    }

    private static String mimeTypeOf(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json") || p.endsWith(".map")) return "application/json";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".ico")) return "image/x-icon";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".ttf")) return "font/ttf";
        if (p.endsWith(".otf")) return "font/otf";
        if (p.endsWith(".wasm")) return "application/wasm";
        if (p.endsWith(".mp3")) return "audio/mpeg";
        if (p.endsWith(".wav")) return "audio/wav";
        if (p.endsWith(".m4a")) return "audio/mp4";
        if (p.endsWith(".zip")) return "application/zip";
        if (p.endsWith(".txt") || p.endsWith(".md")) return "text/plain";
        if (p.endsWith(".webmanifest")) return "application/manifest+json";
        return "application/octet-stream";
    }

    private static String encodingOf(String path) {
        String mime = mimeTypeOf(path);
        if (mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json")
                || mime.contains("svg") || mime.contains("xml")) {
            return "utf-8";
        }
        return null;
    }

    // --------------------------------------------------------------- WebChromeClient

    private class ChatChromeClient extends WebChromeClient {

        @Override
        public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
            // 方便 adb logcat 排查前端问题
            android.util.Log.d("DSWeb", message.message() + " @"
                    + message.sourceId() + ":" + message.lineNumber());
            return true;
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            runOnUiThread(() -> {
                boolean wantsAudio = false;
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                        wantsAudio = true;
                        break;
                    }
                }
                if (!wantsAudio) {
                    request.deny();
                    return;
                }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else {
                    pendingPermissionRequest = request;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                            REQ_MIC_PERMISSION);
                }
            });
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            if (pendingFileCallback != null) {
                pendingFileCallback.onReceiveValue(null);
            }
            pendingFileCallback = callback;
            pendingChooserParams = params;
            clearPickedFiles();
            // 前端「+」面板用 accept 打标记：ds-camera / ds-gallery，其余走文件选择器
            String marker = markerMode(params);
            if ("camera".equals(marker)) {
                takePhoto();
            } else if ("gallery".equals(marker)) {
                launchFilePicker(params, true);
            } else {
                launchFilePicker(params, false);
            }
            return true;
        }
    }

    /** accept 里的前端标记，用来区分拍照 / 相册。 */
    private String markerMode(WebChromeClient.FileChooserParams params) {
        String[] acceptTypes = params.getAcceptTypes();
        if (acceptTypes == null) {
            return null;
        }
        for (String raw : acceptTypes) {
            if (raw == null) {
                continue;
            }
            if (raw.contains("ds-camera")) {
                return "camera";
            }
            if (raw.contains("ds-gallery")) {
                return "gallery";
            }
        }
        return null;
    }

    /** 系统文件/相册选择器。imagesOnly 为 true 时只看图片。 */
    private void launchFilePicker(WebChromeClient.FileChooserParams params, boolean imagesOnly) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String[] mimeTypes = resolveMimeTypes(params);
        if (imagesOnly) {
            intent.setType("image/*");
        } else if (mimeTypes.length > 0) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            intent.setType(mimeTypes[0]);
        } else {
            intent.setType("*/*");
        }
        if (params != null
                && params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "选择文件"), REQ_FILE_CHOOSER);
        } catch (ActivityNotFoundException e) {
            finishChooser(null);
        }
    }

    private void takePhoto() {
        String name = "photo-" + System.currentTimeMillis() + ".jpg";
        pendingCameraUri = Uri.parse("content://" + FileProviderLite.AUTHORITY + "/" + name);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_CAMERA);
        } catch (ActivityNotFoundException e) {
            pendingCameraUri = null;
            finishChooser(null);
        }
    }

    private void finishChooser(Uri[] uris) {
        ValueCallback<Uri[]> callback = pendingFileCallback;
        pendingFileCallback = null;
        if (callback != null) {
            callback.onReceiveValue(uris);
        }
    }

    /**
     * 把 accept=".zip,application/zip,application/x-zip-compressed" 这类声明
     * 归一化成系统文件选择器认识的合法 MIME 列表。
     */
    private String[] resolveMimeTypes(WebChromeClient.FileChooserParams params) {
        String[] acceptTypes = params.getAcceptTypes();
        if (acceptTypes == null) {
            return new String[0];
        }
        MimeTypeMap map = MimeTypeMap.getSingleton();
        java.util.LinkedHashSet<String> mimes = new java.util.LinkedHashSet<>();
        for (String raw : acceptTypes) {
            if (raw == null) {
                continue;
            }
            for (String token : raw.split(",")) {
                String value = token.trim().toLowerCase(Locale.ROOT);
                if (value.isEmpty()) {
                    continue;
                }
                if (value.startsWith(".")) {
                    String mime = map.getMimeTypeFromExtension(value.substring(1));
                    if (mime != null) {
                        mimes.add(mime);
                    }
                } else if (value.contains("/")) {
                    mimes.add(value);
                }
            }
        }
        return mimes.toArray(new String[0]);
    }

    // ------------------------------------------------------------------ 相册

    private String mediaPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private boolean hasMediaPermission() {
        return checkSelfPermission(mediaPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    /** 最近照片列表（JSON）；没有权限时先申请，返回空数组。 */
    private String recentPhotosJson() {
        if (!hasMediaPermission()) {
            runOnUiThread(() -> {
                if (!hasMediaPermission()) {
                    requestPermissions(new String[]{mediaPermission()}, REQ_MEDIA_PERMISSION);
                }
            });
            return "[]";
        }
        JSONArray items = new JSONArray();
        try (android.database.Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME},
                null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            int count = 0;
            while (cursor != null && cursor.moveToNext() && count < 20) {
                String name = cursor.getString(1);
                JSONObject item = new JSONObject();
                item.put("id", String.valueOf(cursor.getLong(0)));
                item.put("name", name == null ? "photo.jpg" : name);
                items.put(item);
                count++;
            }
        } catch (Exception ignored) {
            // 没权限或 provider 异常时返回空列表
        }
        return items.toString();
    }

    private WebResourceResponse serveThumb(String id) {
        if (id == null || id.isEmpty() || !hasMediaPermission()) {
            return notFound();
        }
        try {
            File dir = new File(getCacheDir(), "thumbs");
            if (!dir.exists() && !dir.mkdirs()) {
                return notFound();
            }
            File file = new File(dir, id + ".jpg");
            if (!file.exists()) {
                Bitmap bitmap = loadThumb(id);
                if (bitmap == null) {
                    return notFound();
                }
                try (FileOutputStream out = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out);
                }
                bitmap.recycle();
            }
            return new WebResourceResponse("image/jpeg", null, new FileInputStream(file));
        } catch (Exception e) {
            return notFound();
        }
    }

    private Bitmap loadThumb(String id) {
        long mediaId = Long.parseLong(id);
        Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return getContentResolver().loadThumbnail(uri, new Size(320, 320), null);
            } catch (Exception e) {
                return null;
            }
        }
        return MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(), mediaId,
                MediaStore.Images.Thumbnails.MINI_KIND, null);
    }

    /** 点选最近照片：拷到缓存，再让页面按普通 https 路径取回。 */
    private void pickRecentPhoto(String id) {
        if (id == null || id.isEmpty() || !hasMediaPermission()) {
            return;
        }
        try {
            long mediaId = Long.parseLong(id);
            Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId);
            String name = displayNameOf(uri);
            if (name == null || name.isEmpty()) {
                name = "photo-" + mediaId + ".jpg";
            }
            String mime = getContentResolver().getType(uri);
            File dir = new File(getCacheDir(), "pickedphoto");
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            File target = new File(dir, (mediaId + "-" + name).replaceAll("[\\\\/:*?\"<>|]", "_"));
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(target)) {
                if (in == null) {
                    return;
                }
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            String url = "https://" + ORIGIN_HOST + "/__picked/" + Uri.encode(target.getName());
            String js = "window.__dsAddPhoto&&window.__dsAddPhoto("
                    + JSONObject.quote(url) + "," + JSONObject.quote(target.getName()) + ","
                    + JSONObject.quote(mime == null ? "image/jpeg" : mime) + ")";
            runOnUiThread(() -> webView.evaluateJavascript(js, null));
        } catch (Exception ignored) {
            // 取不到就什么也不做
        }
    }

    private WebResourceResponse servePicked(String encodedName) {
        try {
            String name = Uri.decode(encodedName);
            if (name.contains("..") || name.contains("/")) {
                return notFound();
            }
            File file = new File(new File(getCacheDir(), "pickedphoto"), name);
            if (!file.exists()) {
                return notFound();
            }
            return new WebResourceResponse(mimeTypeOf(name), encodingOf(name),
                    new FileInputStream(file));
        } catch (Exception e) {
            return notFound();
        }
    }

    // ------------------------------------------------------------------ 下载/导出

    /**
     * 前端的"导出历史"用 blob: URL + <a download> 触发下载，WebView 的
     * DownloadListener 拿不到字节流，所以把 blob 读成 base64 交给原生，
     * 再由原生走系统「另存为」。
     */
    private class BlobAwareDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                    String mimetype, long contentLength) {
            if (url != null && url.startsWith("blob:")) {
                String name = "ds-chat-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                        .format(new Date())
                        + (mimetype != null && mimetype.contains("zip") ? ".zip" : ".bin");
                saveBlobToDocument(url, name,
                        mimetype == null || mimetype.isEmpty() ? "application/octet-stream" : mimetype);
            } else {
                openExternally(Uri.parse(url));
            }
        }
    }

    private void saveBlobToDocument(String blobUrl, String fileName, String mimeType) {
        String js = "(function(){"
                + "fetch(" + JSONObject.quote(blobUrl) + ")"
                + ".then(function(r){return r.blob();})"
                + ".then(function(b){"
                + "  var fr=new FileReader();"
                + "  fr.onload=function(){var s=String(fr.result);var i=s.indexOf(',');"
                + "    DSNative.saveBase64(" + JSONObject.quote(fileName) + ","
                + JSONObject.quote(mimeType) + ",s.substring(i+1));};"
                + "  fr.readAsDataURL(b);"
                + "}).catch(function(e){console.log('blob export failed',e);});"
                + "})()";
        webView.evaluateJavascript(js, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_CAMERA) {
            Uri uri = pendingCameraUri;
            pendingCameraUri = null;
            finishChooser(resultCode == RESULT_OK && uri != null ? new Uri[]{uri} : null);
            return;
        }

        if (requestCode == REQ_FILE_CHOOSER) {
            if (pendingFileCallback == null) {
                return;
            }
            ValueCallback<Uri[]> callback = pendingFileCallback;
            pendingFileCallback = null;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            }
            if (results == null || results.length == 0) {
                callback.onReceiveValue(null);
                return;
            }
            // content:// 大文件在 WebView 里是按小段反复 open 的，走 MediaProvider/FUSE 会慢到不可用。
            // 先拷到应用缓存，再把 file:// 交给网页，读取就是普通本地文件。
            final Uri[] picked = results;
            fileCopyExecutor.execute(() -> {
                Uri[] local = new Uri[picked.length];
                for (int i = 0; i < picked.length; i++) {
                    local[i] = copyPickedFile(picked[i], i);
                }
                runOnUiThread(() -> callback.onReceiveValue(local));
            });
            return;
        }

        if (requestCode == REQ_CREATE_DOCUMENT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null
                    && pendingDocumentBytes != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out != null) {
                        out.write(pendingDocumentBytes);
                        out.flush();
                        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException e) {
                    Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            pendingDocumentBytes = null;
        }
    }

    private static final String SYNC_PREFS = "dsnative-sync";

    private SharedPreferences syncPrefs() {
        return getSharedPreferences(SYNC_PREFS, MODE_PRIVATE);
    }

    /** 暴露给网页的桥：保存文件、最近照片、局域网同步。 */
    private class NativeBridge {
        @android.webkit.JavascriptInterface
        public String syncConfig() {
            SharedPreferences prefs = syncPrefs();
            try {
                JSONObject out = new JSONObject();
                out.put("base", prefs.getString("base", ""));
                out.put("token", prefs.getString("token", ""));
                out.put("lastSync", prefs.getLong("lastSync", 0L));
                return out.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @android.webkit.JavascriptInterface
        public void setSyncConfig(String base, String token) {
            syncPrefs().edit()
                    .putString("base", base == null ? "" : base.trim())
                    .putString("token", token == null ? "" : token.trim())
                    .apply();
        }

        @android.webkit.JavascriptInterface
        public void setLastSync(long millis) {
            syncPrefs().edit().putLong("lastSync", millis).apply();
        }

        /**
         * 同步请求转发：网页同源请求会被 CORS/混合内容拦住，所以由原生发 HTTP。
         * path 形如 "index"、"sessions/xxx"、"files/yyy?session=xxx"。
         * 返回 JSON：{status, body(base64)} 或 {error}。
         */
        @android.webkit.JavascriptInterface
        public String syncRequest(String method, String path, String bodyBase64, String contentType) {
            SharedPreferences prefs = syncPrefs();
            String base = prefs.getString("base", "");
            String token = prefs.getString("token", "");
            if (base == null || base.isEmpty()) {
                return "{\"error\":\"未配置同步地址\"}";
            }
            String url = base.replaceAll("/+$", "") + "/dsh-sync/" + path;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(60000);
                connection.setRequestProperty("X-Pair-Token", token);
                if (contentType != null && !contentType.isEmpty()) {
                    connection.setRequestProperty("Content-Type", contentType);
                }
                byte[] payload = null;
                if (bodyBase64 != null && !bodyBase64.isEmpty()) {
                    payload = Base64.decode(bodyBase64, Base64.DEFAULT);
                    connection.setDoOutput(true);
                    connection.setFixedLengthStreamingMode(payload.length);
                    OutputStream out = connection.getOutputStream();
                    out.write(payload);
                    out.close();
                }
                int status = connection.getResponseCode();
                InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                if (input != null) {
                    byte[] chunk = new byte[8192];
                    int read;
                    while ((read = input.read(chunk)) > 0) {
                        buffer.write(chunk, 0, read);
                    }
                    input.close();
                }
                JSONObject out = new JSONObject();
                out.put("status", status);
                out.put("body", Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP));
                return out.toString();
            } catch (Exception e) {
                try {
                    JSONObject out = new JSONObject();
                    out.put("error", String.valueOf(e.getMessage()));
                    return out.toString();
                } catch (Exception ignored) {
                    return "{\"error\":\"网络请求失败\"}";
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        @android.webkit.JavascriptInterface
        public String recentPhotos() {
            return recentPhotosJson();
        }

        @android.webkit.JavascriptInterface
        public void pickPhoto(String id) {
            pickRecentPhoto(id);
        }

        @android.webkit.JavascriptInterface
        public void saveBase64(String fileName, String mimeType, String base64) {
            if (base64 == null) {
                return;
            }
            byte[] bytes;
            try {
                bytes = Base64.decode(base64, Base64.DEFAULT);
            } catch (IllegalArgumentException e) {
                return;
            }
            runOnUiThread(() -> {
                pendingDocumentBytes = bytes;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(mimeType == null || mimeType.isEmpty()
                        ? "application/octet-stream" : mimeType);
                intent.putExtra(Intent.EXTRA_TITLE, fileName == null ? "export.bin" : fileName);
                try {
                    startActivityForResult(intent, REQ_CREATE_DOCUMENT);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "没有文件管理器可用",
                            Toast.LENGTH_SHORT).show();
                    pendingDocumentBytes = null;
                }
            });
        }
    }

    /**
     * 把选中的 content:// 文件拷到缓存目录，返回 file:// URI；失败时退回原 URI。
     * WebView 读取 content:// 大文件时会反复 open（走 MediaProvider/FUSE），
     * 导入 100MB+ 的历史包时会慢到不可用。
     */
    private Uri copyPickedFile(Uri source, int index) {
        if (source == null || !"content".equals(source.getScheme())) {
            return source;
        }
        // 相机拍的照片已经在我们自己的缓存里，不用再拷
        if (FileProviderLite.AUTHORITY.equals(source.getAuthority())) {
            return source;
        }
        String name = displayNameOf(source);
        String ext = "";
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && name.length() - dot <= 8) {
                ext = name.substring(dot);
            }
        }
        File dir = new File(getCacheDir(), "picked");
        if (!dir.exists() && !dir.mkdirs()) {
            return source;
        }
        File target = new File(dir, "picked-" + index + ext);
        try (InputStream in = getContentResolver().openInputStream(source);
             OutputStream out = new java.io.FileOutputStream(target)) {
            if (in == null) {
                return source;
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            return Uri.fromFile(target);
        } catch (IOException | SecurityException e) {
            return source;
        }
    }

    private String displayNameOf(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (RuntimeException ignored) {
            // 某些 provider 不支持查询
        }
        return null;
    }

    private void clearPickedFiles() {
        File dir = new File(getCacheDir(), "picked");
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    // ------------------------------------------------------------------ 权限回调

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MEDIA_PERMISSION) {
            if (hasMediaPermission()) {
                runOnUiThread(() -> webView.evaluateJavascript(
                        "window.__dsPhotosReady&&window.__dsPhotosReady()", null));
            }
            return;
        }

        if (requestCode == REQ_MIC_PERMISSION && pendingPermissionRequest != null) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                pendingPermissionRequest.grant(
                        new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                pendingPermissionRequest.deny();
                Toast.makeText(this, "需要麦克风权限才能语音输入", Toast.LENGTH_SHORT).show();
            }
            pendingPermissionRequest = null;
        }
    }

    // ------------------------------------------------------------------ 生命周期

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPress < 2000) {
            super.onBackPressed();
            return;
        }
        lastBackPress = now;
        Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        fileCopyExecutor.shutdownNow();
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
