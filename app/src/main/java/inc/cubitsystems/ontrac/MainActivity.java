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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_PICK = 1001;
    private static final int REQ_CAMERA = 1002;
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

    private File vaultDir() {
        File dir = new File(getFilesDir(), "case_vault/" + (pendingCaseId.isEmpty() ? "default" : pendingCaseId));
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private String saveToCaseVault(Uri uri) {
        try {
            String name = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
            File out = new File(vaultDir(), name);
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
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            eval("window.onTracCaptureResult && window.onTracCaptureResult(false, 'cancelled')");
            return;
        }
        if (requestCode == REQ_CAMERA && cameraFile != null && cameraFile.exists()) {
            eval("window.onTracCaptureResult && window.onTracCaptureResult(true, '" + esc(cameraFile.getAbsolutePath()) + "')");
            Toast.makeText(this, "Photo saved to case vault", Toast.LENGTH_SHORT).show();
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
        public void requestDocumentCapture(String caseId, String consentId) {
            pendingCaseId = caseId != null ? caseId : "";
            runOnUiThread(() -> {
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.setType("image/*");
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    cameraFile = new File(vaultDir(), "cap_" + System.currentTimeMillis() + ".jpg");
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
                return new String(data);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void openExternal(String url) {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(i);
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
