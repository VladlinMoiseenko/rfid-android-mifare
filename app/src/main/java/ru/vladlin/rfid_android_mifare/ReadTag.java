package ru.vladlin.rfid_android_mifare;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.SparseArray;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;

import static ru.vladlin.rfid_android_mifare.Preferences.Preference.UseInternalStorage;

/**
 * Create a key map with the {@link KeyMapCreator} and then
 * read the tag.
 */
public class ReadTag extends Activity {

    private final static int KEY_MAP_CREATOR = 1;

    private final Handler mHandler = new Handler();
    private SparseArray<String[]> mRawDump;

    /**
     * Check for external storage and show the {@link KeyMapCreator}.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_read_tag);

        if (!Common.getPreferences().getBoolean(UseInternalStorage.toString(),
                false) && !Common.isExternalStorageWritableErrorToast(this)) {
            finish();
            return;
        }

        Intent intent = new Intent(this, KeyMapCreator.class);
        intent.putExtra(KeyMapCreator.EXTRA_KEYS_DIR,
                Common.getFileFromStorage(Common.HOME_DIR + "/" +
                        Common.KEYS_DIR).getAbsolutePath());
        intent.putExtra(KeyMapCreator.EXTRA_BUTTON_TEXT,
                getString(R.string.action_create_key_map_and_read));
        startActivityForResult(intent, KEY_MAP_CREATOR);
    }

    /**
     * Checks the result code of the key mapping process. If the process
     * was successful the {@link #readTag()}
     * method will be called.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
        case KEY_MAP_CREATOR:
            if (resultCode != Activity.RESULT_OK) {
                // Error.
                if (resultCode == 4) {
                    // Error. Path from the calling intend was null.
                    // (This is really strange and should not occur.)
                    Toast.makeText(this, R.string.info_strange_error,
                            Toast.LENGTH_LONG).show();
                }
                finish();
                return;
            } else {
                // Read Tag.
                readTag();
            }
            break;
        }
    }

    /**
     * Triggered by {@link #onActivityResult(int, int, Intent)}
     * this method starts a worker thread that first reads the tag and then
     * calls {@link #createTagDump(SparseArray)}.
     */
    private void readTag() {
        final MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            return;
        }
        new Thread(() -> {
            // Get key map from glob. variable.
            mRawDump = reader.readAsMuchAsPossible(
                    Common.getKeyMap());

            reader.close();

            mHandler.post(() -> createTagDump(mRawDump));
        }).start();
    }

    /**
     * Create a tag dump in a format the {@link DumpEditor}
     * can read (format: headers (sectors) marked with "+", errors
     * marked with "*"), and then start the dump editor with this dump.
     * @param rawDump A tag dump like {@link MCReader#readAsMuchAsPossible()}
     * returns.
     * @see DumpEditor#EXTRA_DUMP
     * @see DumpEditor
     */
    private void createTagDump(SparseArray<String[]> rawDump) {
        ArrayList<String> tmpDump = new ArrayList<>();
        if (rawDump != null) {
            if (rawDump.size() != 0) {
                for (int i = Common.getKeyMapRangeFrom();
                        i <= Common.getKeyMapRangeTo(); i++) {
                    String[] val = rawDump.get(i);
                    // Mark headers (sectors) with "+".
                    tmpDump.add("+Sector: " + i);
                    if (val != null ) {
                        Collections.addAll(tmpDump, val);
                    } else {
                        // Mark sector as not readable ("*").
                        tmpDump.add("*No keys found or dead sector");
                    }
                }
                String[] dump = tmpDump.toArray(new String[tmpDump.size()]);

                // Show Dump Editor Activity.
                Intent intent = new Intent(this, DumpEditor.class);
                intent.putExtra(DumpEditor.EXTRA_DUMP, dump);
                startActivity(intent);
            } else {
                // Error, keys from key map are not valid for reading.
                Toast.makeText(this, R.string.info_none_key_valid_for_reading,
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, R.string.info_tag_removed_while_reading,
                    Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
