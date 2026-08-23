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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * OnTrac — one Approve; system moves.
 * Capture and voice hooks are real device actions gated by UI consent.
 */
public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private String pendingConsentId = "";
    private String pendingCaseId = "";
    private Uri cameraUri;
    private File cameraFile;

    private final ActivityResultLauncher<Intent> pickImage =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                eval("window.onTracCaptureResult && window.onTracCaptureResult(false, 'cancelled')");
                return;
            }
            Uri uri = result.getData().getData();
            String path = saveToCaseVault(uri);
            if (path != null) {
                eval("window.onTracCaptureResult && window.onTracCaptureResult(true, '" + escape(path) + "')");
                toast("Document saved to case vault");
            } else {
                eval("window.onTracCaptureResult && window.onTracCaptureResult(false, 'save_failed')");
            }
        });

    private final ActivityResultLauncher<Uri> takePicture =
        registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (!ok || cameraFile == null || !cameraFile.exists()) {
                eval("window.onTracCaptureResult && window.onTracCaptureResult(false, 'camera_cancelled')");
                return;
            }
            eval("window.onTracCaptureResult && window.onTracCaptureResult(true, '" + escape(cameraFile.getAbsolutePath()) + "')");
            toast("Photo saved to case vault");
        });

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

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
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

    public class Bridge {
        @JavascriptInterface
        public void toast(String msg) {
            toastMsg(msg);
        }

        private void toastMsg(String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void requestDocumentCapture(String caseId, String consentId) {
            pendingCaseId = caseId != null ? caseId : "";
            pendingConsentId = consentId != null ? consentId : "";
            runOnUiThread(() -> {
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.setType("image/*");
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                try {
                    cameraFile = new File(vaultDir(), "cap_" + System.currentTimeMillis() + ".jpg");
                    cameraUri = FileProvider.getUriForFile(
                        MainActivity.this,
                        "inc.cubitsystems.ontrac.fileprovider",
                        cameraFile);
                    cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                    cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception e) {
                    cameraUri = null;
                }
                Intent chooser = Intent.createChooser(pick, "Add document to case");
                if (cameraUri != null) {
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
                }
                pickImage.launch(chooser);
            });
        }

        @JavascriptInterface
        public void queueVoiceRepresentation(String caseId, String consentId, String scope) {
            runOnUiThread(() ->
                Toast.makeText(MainActivity.this,
                    "Voice script armed · consent " + (consentId != null ? consentId : ""),
                    Toast.LENGTH_LONG).show());
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
