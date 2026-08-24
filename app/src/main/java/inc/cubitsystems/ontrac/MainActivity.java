package inc.cubitsystems.ontrac;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_PICK = 1001;
    private WebView webView;
    private String pendingCaseId = "";
    private Uri cameraUri;
    private File cameraFile;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        try {
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
        } catch (Throwable ignored) {}
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Bridge(), "OnTracNative");
        webView.loadUrl("file:///android_asset/ontrac/index.html");
    }

    private void eval(String js) {
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    private File casesDir() {
        File dir = new File(getFilesDir(), "cases");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private File vaultDir(String caseId) {
        String id = (caseId == null || caseId.isEmpty()) ? "default" : caseId;
        File dir = new File(getFilesDir(), "case_vault/" + id);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private String saveToCaseVault(Uri uri) {
        try {
            String name = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
            File out = new File(vaultDir(pendingCaseId), name);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(out)) {
                if (in == null) return null;
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) fos.write(buf, 0, n);
            }
            return out.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
    }

    private static String jsonEsc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            eval("window.onTracCaptureResult && window.onTracCaptureResult(false, 'cancelled')");
            return;
        }
        if (requestCode == REQ_PICK && data != null && data.getData() != null) {
            String path = saveToCaseVault(data.getData());
            if (path != null) {
                eval("window.onTracCaptureResult && window.onTracCaptureResult(true, '" + esc(path) + "')");
                Toast.makeText(this, "Document saved to case vault", Toast.LENGTH_SHORT).show();
            } else {
                eval("window.onTracCaptureResult && window.onTracCaptureResult(false, 'save_failed')");
            }
        }
    }

    public class Bridge {
        @JavascriptInterface
        public void toast(String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void saveCase(String caseId, String json) {
            try {
                if (caseId == null || caseId.isEmpty()) return;
                File f = new File(casesDir(), caseId + ".json");
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    fos.write(json.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Save failed", Toast.LENGTH_SHORT).show());
            }
        }

        @JavascriptInterface
        public String loadCase(String caseId) {
            try {
                File f = new File(casesDir(), caseId + ".json");
                if (!f.exists()) return "";
                byte[] data = new byte[(int) f.length()];
                try (FileInputStream in = new FileInputStream(f)) {
                    //noinspection ResultOfMethodCallIgnored
                    in.read(data);
                }
                return new String(data, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public String listCases() {
            try {
                File[] files = casesDir().listFiles((dir, name) -> name.endsWith(".json"));
                if (files == null || files.length == 0) return "[]";
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (File f : files) {
                    try {
                        byte[] data = new byte[(int) f.length()];
                        try (FileInputStream in = new FileInputStream(f)) {
                            //noinspection ResultOfMethodCallIgnored
                            in.read(data);
                        }
                        String json = new String(data, StandardCharsets.UTF_8);
                        if (!first) sb.append(",");
                        first = false;
                        sb.append(json);
                    } catch (Exception ignored) {}
                }
                sb.append("]");
                return sb.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public void deleteCase(String caseId) {
            try {
                File f = new File(casesDir(), caseId + ".json");
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public String listVault(String caseId) {
            try {
                File dir = vaultDir(caseId);
                File[] files = dir.listFiles();
                if (files == null || files.length == 0) return "[]";
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (File f : files) {
                    if (!f.isFile()) continue;
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{\"name\":\"").append(jsonEsc(f.getName())).append("\"");
                    sb.append(",\"path\":\"").append(jsonEsc(f.getAbsolutePath())).append("\"");
                    sb.append(",\"size\":").append(f.length());
                    sb.append(",\"modified\":").append(f.lastModified());
                    sb.append("}");
                }
                sb.append("]");
                return sb.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public boolean deleteVaultFile(String caseId, String name) {
            try {
                if (name == null || name.contains("..") || name.contains("/")) return false;
                File f = new File(vaultDir(caseId), name);
                return f.isFile() && f.delete();
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void requestDocumentCapture(String caseId, String consentId) {
            pendingCaseId = caseId != null ? caseId : "";
            runOnUiThread(() -> {
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.setType("image/*");
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    cameraFile = new File(vaultDir(pendingCaseId), "cap_" + System.currentTimeMillis() + ".jpg");
                    cameraUri = FileProvider.getUriForFile(
                        MainActivity.this, "inc.cubitsystems.ontrac.fileprovider", cameraFile);
                    Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                    cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    Intent chooser = Intent.createChooser(pick, "Add document to case");
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
                    startActivityForResult(chooser, REQ_PICK);
                } catch (Exception e) {
                    startActivityForResult(Intent.createChooser(pick, "Add document to case"), REQ_PICK);
                }
            });
        }

        @JavascriptInterface
        public void queueVoiceRepresentation(String caseId, String consentId, String scope) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Voice script armed", Toast.LENGTH_LONG).show());
        }

        @JavascriptInterface
        public String loadScript(String name) {
            try {
                InputStream in = getAssets().open("ontrac/scripts/" + name);
                byte[] data = new byte[in.available()];
                //noinspection ResultOfMethodCallIgnored
                in.read(data);
                in.close();
                return new String(data, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void openExternal(String url) {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Cannot open link", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void shareOutreach(String subject, String body) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, subject);
                send.putExtra(Intent.EXTRA_TEXT, body);
                startActivity(Intent.createChooser(send, "Send outreach via…"));
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
