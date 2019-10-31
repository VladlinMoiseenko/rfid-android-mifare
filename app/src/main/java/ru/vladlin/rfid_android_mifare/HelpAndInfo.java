package ru.vladlin.rfid_android_mifare;

import android.os.Bundle;
import android.webkit.WebView;

public class HelpAndInfo extends BasicActivity {

    /**
     * Initialize the layout and the web view (browser) on local
     * help website.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        // Init. help from local website.
        WebView wv = findViewById(R.id.webViewHelpText);
        wv.loadUrl("file:///android_asset/help/help.html");
    }
}
