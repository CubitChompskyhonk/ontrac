package inc.cubitsystems.ontrac;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * OnTrac caseworker shell.
 * Doctrine: user Approves; system executes. Delegating the hard step to the user is a product failure.
 */
public class MainActivity extends AppCompatActivity {
    private WebView webView;

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

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    public class Bridge {
        @JavascriptInterface
        public void toast(String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }

        /** Placeholder for Assimilate document capture — only after consent in UI */
        @JavascriptInterface
        public void requestDocumentCapture(String caseId, String consentId) {
            runOnUiThread(() ->
                Toast.makeText(MainActivity.this,
                    "Assimilate capture queued (consent " + consentId + ")",
                    Toast.LENGTH_LONG).show());
        }

        /** Placeholder for voice representation pipeline */
        @JavascriptInterface
        public void queueVoiceRepresentation(String caseId, String consentId, String scope) {
            runOnUiThread(() ->
                Toast.makeText(MainActivity.this,
                    "Voice representation queued under consent",
                    Toast.LENGTH_LONG).show());
        }
    }
}
