package ru.vladlin.rfid_android_mifare;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;

public class HexToAscii extends BasicActivity {

    private static final String LOG_TAG =
            HexToAscii.class.getSimpleName();

    /**
     * Initialize the activity with the data from the Intent
     * ({@link DumpEditor#EXTRA_DUMP}) by displaying them as
     * US-ASCII. Non printable ASCII characters will be displayed as ".".
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hex_to_ascii);

        if (getIntent().hasExtra(DumpEditor.EXTRA_DUMP)) {
            String[] dump = getIntent().getStringArrayExtra(
                    DumpEditor.EXTRA_DUMP);
            if (dump.length != 0) {
                String s = System.getProperty("line.separator");
                CharSequence ascii = "";
                for (String line : dump) {
                    if (line.startsWith("+")) {
                        // Header.
                        String sectorNumber = line.split(": ")[1];
                        ascii = TextUtils.concat(ascii, Common.colorString(
                                getString(R.string.text_sector)
                                + ": " + sectorNumber,
                                getResources().getColor(R.color.blue)), s);
                    } else {
                        // Data.
                        // Replace non printable ASCII with ".".
                        byte[] hex = Common.hexStringToByteArray(line);
                        for(int i = 0; i < hex.length; i++) {
                            if (hex[i] < (byte)0x20 || hex[i] == (byte)0x7F) {
                                hex[i] = (byte)0x2E;
                            }
                        }
                        // Hex to ASCII.
                        try {
                            ascii = TextUtils.concat(ascii, " ",
                                    new String(hex, "US-ASCII"), s);
                        } catch (UnsupportedEncodingException e) {
                            Log.e(LOG_TAG, "Error while encoding to ASCII", e);
                        }
                    }
                }
                TextView tv = findViewById(R.id.textViewHexToAscii);
                tv.setText(ascii);
            }
            setIntent(null);
        }
    }
}
