package ru.vladlin.rfid_android_mifare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;

import static ru.vladlin.rfid_android_mifare.Preferences.Preference.UseInternalStorage;

public class FileChooser extends BasicActivity {

    // Input parameters.
    /**
     * Path to a directory with files. The files in the directory
     * are the files the user can choose from. This must be in the Intent.
     */
    public final static String EXTRA_DIR =
            "de.syss.MifareClassicTool.Activity.DIR";
    /**
     * The title of the activity. Optional.
     * e.g. "Open Dump File"
     */
    public final static String EXTRA_TITLE =
            "de.syss.MifareClassicTool.Activity.TITLE";
    /**
     * The small text above the files. Optional.
     * e.g. "Please choose a file:
     */
    public final static String EXTRA_CHOOSER_TEXT =
            "de.syss.MifareClassicTool.Activity.CHOOSER_TEXT";
    /**
     * The text of the choose button. Optional.
     * e.g. "Open File"
     */
    public final static String EXTRA_BUTTON_TEXT =
            "de.syss.MifareClassicTool.Activity.BUTTON_TEXT";
    /**
     * Enable/Disable the menu item  that allows the user to create a new file.
     * Optional. Boolean value. Disabled (false) by default.
     */
    public final static String EXTRA_ENABLE_NEW_FILE =
            "de.syss.MifareClassicTool.Activity.ENABLE_NEW_FILE";
    /**
     * Enable/Disable the menu item  that allows the user to delete a file.
     * Optional. Boolean value. Disabled (false) by default.
     */
    public final static String EXTRA_ENABLE_DELETE_FILE =
            "de.syss.MifareClassicTool.Activity.ENABLE_DELETE_FILE";


    // Output parameter.
    /**
     * The file (with full path) that will be passed via Intent
     * to onActivityResult() method. The result code will be
     * {@link Activity#RESULT_OK}.
     */
    public final static String EXTRA_CHOSEN_FILE =
            "de.syss.MifareClassicTool.Activity.CHOSEN_FILE";
    /**
     * The filename (without path) that will be passed via Intent
     * to onActivityResult() method. The result code will be
     * {@link Activity#RESULT_OK}.
     */
    public final static String EXTRA_CHOSEN_FILENAME =
            "de.syss.MifareClassicTool.Activity.EXTRA_CHOSEN_FILENAME";


    private static final String LOG_TAG =
            FileChooser.class.getSimpleName();
    private RadioGroup mGroupOfFiles;
    private Button mChooserButton;
    private TextView mChooserText;
    private MenuItem mDeleteFile;
    private File mDir;
    private boolean mIsDirEmpty;
    private boolean mCreateFileEnabled = false;
    private boolean mDeleteFileEnabled = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_chooser);
        mGroupOfFiles = findViewById(R.id.radioGroupFileChooser);
    }

    /**
     * Initialize the file chooser with the data from the calling Intent
     * (if external storage is mounted).
     * @see #EXTRA_DIR
     * @see #EXTRA_TITLE
     * @see #EXTRA_CHOOSER_TEXT
     * @see #EXTRA_BUTTON_TEXT
     */
    @Override
    public void onStart() {
        super.onStart();

        if (!Common.getPreferences().getBoolean(UseInternalStorage.toString(),
                false) && !Common.isExternalStorageWritableErrorToast(this)) {
            setResult(3);
            finish();
            return;
        }
        mChooserText = findViewById(
                R.id.textViewFileChooser);
        mChooserButton = findViewById(
                R.id.buttonFileChooserChoose);
        Intent intent = getIntent();

        // Set title.
        if (intent.hasExtra(EXTRA_TITLE)) {
            setTitle(intent.getStringExtra(EXTRA_TITLE));
        }
        // Set chooser text.
        if (intent.hasExtra(EXTRA_CHOOSER_TEXT)) {
            mChooserText.setText(intent.getStringExtra(EXTRA_CHOOSER_TEXT));
        }
        // Set button text.
        if (intent.hasExtra(EXTRA_BUTTON_TEXT)) {
            mChooserButton.setText(intent.getStringExtra(EXTRA_BUTTON_TEXT));
        }
        // Remember to enable/disable new file functionality.
        if (intent.hasExtra(EXTRA_ENABLE_NEW_FILE)) {
            mCreateFileEnabled = intent.getBooleanExtra(
                        EXTRA_ENABLE_NEW_FILE, false);
        }
        // Remember to enable/disable new file functionality.
        if (intent.hasExtra(EXTRA_ENABLE_DELETE_FILE)) {
            mDeleteFileEnabled = intent.getBooleanExtra(
                        EXTRA_ENABLE_DELETE_FILE, false);
        }


        // Check path and initialize file list.
        if (intent.hasExtra(EXTRA_DIR)) {
            File path = new File(intent.getStringExtra(EXTRA_DIR));
            if (path.exists()) {
                if (!path.isDirectory()) {
                    setResult(4);
                    finish();
                    return;
                }
                mDir = path;
                mIsDirEmpty = updateFileIndex(path);
            } else {
                // Path does not exist.
                Log.e(LOG_TAG, "Directory for FileChooser does not exist.");
                setResult(1);
                finish();
            }
        } else {
            Log.d(LOG_TAG, "Directory for FileChooser was not in intent.");
            setResult(2);
            finish();
        }
    }

    /**
     * Add the menu to the Activity.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.file_chooser_functions, menu);
        menu.findItem(R.id.menuFileChooserNewFile)
                .setEnabled(mCreateFileEnabled);
        mDeleteFile = menu.findItem(R.id.menuFileChooserDeleteFile);
        // Only use the enable/disable system for the delete file menu item
        // if there is a least one file.
        if (!mIsDirEmpty) {
            mDeleteFile.setEnabled(mDeleteFileEnabled);
        } else {
            mDeleteFile.setEnabled(false);
        }
        return true;
    }

    /**
     * Handle selected function form the menu (create new file, delete file).
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection.
        switch (item.getItemId()) {
        case R.id.menuFileChooserNewFile:
            onNewFile();
            return true;
        case R.id.menuFileChooserDeleteFile:
            onDeleteFile();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Finish the Activity with an Intent containing
     * {@link #EXTRA_CHOSEN_FILE} and {@link #EXTRA_CHOSEN_FILENAME} as result.
     * You can catch that result by overriding onActivityResult() in the
     * Activity that called the file chooser via startActivityForResult().
     * @param view The View object that triggered the function
     * (in this case the choose file button).
     * @see #EXTRA_CHOSEN_FILE
     * @see #EXTRA_CHOSEN_FILENAME
     */
    public void onFileChosen(View view) {
        RadioButton selected = findViewById(
                mGroupOfFiles.getCheckedRadioButtonId());

        Intent intent = new Intent();
        File file = new File(mDir.getPath(), selected.getText().toString());
        intent.putExtra(EXTRA_CHOSEN_FILE, file.getPath());
        intent.putExtra(EXTRA_CHOSEN_FILENAME, file.getName());
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    /**
     * Update the file list and the components that depend on it
     * (e.g. disable the open file button if there is no file).
     * @param path Path to the directory which will be listed.
     * @return True if directory is empty. False otherwise.
     */
    private boolean updateFileIndex(File path) {
        File[] files = path.listFiles();

        // Refresh file list.
        if (files != null && files.length > 0) {
            Arrays.sort(files);
            mGroupOfFiles.removeAllViews();

            for(File f : files) {
                RadioButton r = new RadioButton(this);
                r.setText(f.getName());
                mGroupOfFiles.addView(r);
            }
            // Check first file.
            ((RadioButton)mGroupOfFiles.getChildAt(0)).setChecked(true);
            mChooserButton.setEnabled(true);
            if (mDeleteFile != null) {
                mDeleteFile.setEnabled(mDeleteFileEnabled);
            }
            return false;
        } else {
            // No files in directory.
            mChooserButton.setEnabled(false);
            if (mDeleteFile != null) {
                mDeleteFile.setEnabled(false);
            }
            Intent intent = getIntent();
            String chooserText = "";
            if (intent.hasExtra(EXTRA_CHOOSER_TEXT)) {
                chooserText = intent.getStringExtra(EXTRA_CHOOSER_TEXT);
            }
            mChooserText.setText(chooserText
                    + "\n   --- "
                    + getString(R.string.text_no_files_in_chooser)
                    + " ---");
        }
        return true;
    }

    /**
     * Ask the user for a file name, create this file and choose it.
     * ({@link #onFileChosen(View)}).
     */
    private void onNewFile() {
        final Context cont = this;
        // Ask user for filename.
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setLines(1);
        input.setHorizontallyScrolling(true);
        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_new_file_title)
            .setMessage(R.string.dialog_new_file)
            .setIcon(android.R.drawable.ic_menu_add)
            .setView(input)
            .setPositiveButton(R.string.action_ok,
                    (dialog, whichButton) -> {
                        if (input.getText() != null
                                && !input.getText().toString().equals("")) {
                            File file = new File(mDir.getPath(),
                                    input.getText().toString());
                            if (file.exists()) {
                                Toast.makeText(cont,
                                        R.string.info_file_already_exists,
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            Intent intent = new Intent();
                            intent.putExtra(EXTRA_CHOSEN_FILE, file.getPath());
                            setResult(Activity.RESULT_OK, intent);
                            finish();
                        } else {
                            // Empty name is not allowed.
                            Toast.makeText(cont, R.string.info_empty_file_name,
                                    Toast.LENGTH_LONG).show();
                        }
                    })
            .setNegativeButton(R.string.action_cancel,
                    (dialog, whichButton) -> {
                        // Do nothing.
                    }).show();
    }

    /**
     * Delete the selected file and update the file list.
     * @see #updateFileIndex(File)
     */
    private void onDeleteFile() {
        RadioButton selected = findViewById(
                mGroupOfFiles.getCheckedRadioButtonId());
        File file  = new File(mDir.getPath(), selected.getText().toString());
        file.delete();
        mIsDirEmpty = updateFileIndex(mDir);
    }
}
