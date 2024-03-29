package ru.vladlin.rfid_android_mifare;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Locale;

import static ru.vladlin.rfid_android_mifare.Preferences.Preference.UseInternalStorage;

public class DumpEditor extends BasicActivity
        implements IActivityThatReactsToSave {

    /**
     * The corresponding Intent will contain a dump separated by new lines.
     * Headers (e.g. "Sector: 1") are marked with a "+"-symbol
     * (e.g. "+Sector: 1"). Errors (e.g. "No keys found or dead sector")
     * are marked with a "*"-symbol.
     */
    public final static String EXTRA_DUMP =
            "de.syss.MifareClassicTool.Activity.DUMP";

    private static final String LOG_TAG =
            DumpEditor.class.getSimpleName();

    private LinearLayout mLayout;
    private String mDumpName;
    private String mKeysName;
    private String mUID;

    /**
     * All blocks containing valid data AND their headers (marked with "+"
     * e.g. "+Sector: 0") as strings.
     * This will be updated with every {@link #checkDumpAndUpdateLines()}
     * check.
     */
    private String[] mLines;

    /**
     * True if the user made changes to the dump.
     * Used by the "save before quitting" dialog.
     */
    private boolean mDumpChanged;

    /**
     * If true, the editor will close after a successful save.
     * @see #onSaveSuccessful()
     */
    private boolean mCloseAfterSuccessfulSave;


    /**
     * Check whether to initialize the editor on a dump file or on
     * a new dump directly from {@link ReadTag}
     * (or recreate instance state if the activity was killed).
     * Also it will color the caption of the dump editor.
     * @see #initEditor(String[])
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dump_editor);

        mLayout = findViewById(
                R.id.linearLayoutDumpEditor);

        // Color caption.
        SpannableString keyA = Common.colorString(
                getString(R.string.text_keya),
                getResources().getColor(R.color.light_green));
        SpannableString keyB =  Common.colorString(
                getString(R.string.text_keyb),
                getResources().getColor(R.color.dark_green));
        SpannableString ac = Common.colorString(
                getString(R.string.text_ac),
                getResources().getColor(R.color.orange));
        SpannableString uidAndManuf = Common.colorString(
                getString(R.string.text_uid_and_manuf),
                getResources().getColor(R.color.purple));
        SpannableString vb = Common.colorString(
                getString(R.string.text_valueblock),
                getResources().getColor(R.color.yellow));

        TextView caption = findViewById(
                R.id.textViewDumpEditorCaption);
        caption.setText(TextUtils.concat(uidAndManuf, " | ",
                vb, " | ", keyA, " | ", keyB, " | ", ac), BufferType.SPANNABLE);
        // Add web-link optic to update colors text view (= caption title).
        TextView captionTitle = findViewById(
                R.id.textViewDumpEditorCaptionTitle);
        SpannableString updateText = Common.colorString(
                getString(R.string.text_update_colors),
                getResources().getColor(R.color.blue));
        updateText.setSpan(new UnderlineSpan(), 0, updateText.length(), 0);
        captionTitle.setText(TextUtils.concat(
                getString(R.string.text_caption_title),
                ": (", updateText, ")"));

        if (getIntent().hasExtra(EXTRA_DUMP)) {
            // Called from ReadTag (init editor by intent).
            String[] dump = getIntent().getStringArrayExtra(EXTRA_DUMP);
            // Set title with UID.
            if (Common.getUID() != null) {
                mUID = Common.byte2HexString(Common.getUID());
                setTitle(getTitle() + " (UID: " + mUID+ ")");
            }
            initEditor(dump);
            setIntent(null);
        } else if (getIntent().hasExtra(
                FileChooser.EXTRA_CHOSEN_FILE)) {
            // Called form FileChooser (init editor by file).
            File file = new File(getIntent().getStringExtra(
                    FileChooser.EXTRA_CHOSEN_FILE));
            mDumpName = file.getName();
            setTitle(getTitle() + " (" + mDumpName + ")");
            initEditor(Common.readFileLineByLine(file, false, this));
            setIntent(null);
        } else if (savedInstanceState != null) {
            // Recreated after kill by Android (due to low memory).
            mDumpName = savedInstanceState.getString("file_name");
            if (mDumpName != null) {
                setTitle(getTitle() + " (" + mDumpName + ")");
            }
            mLines = savedInstanceState.getStringArray("lines");
            if (mLines != null) {
                initEditor(mLines);
            }
        }
    }

    /**
     * Add a menu with editor functions to the Activity.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.dump_editor_functions, menu);
        // Enable/Disable write dump function depending on NFC availability.
        menu.findItem(R.id.menuDumpEditorWriteDump).setEnabled(
                !Common.useAsEditorOnly());
        return true;
    }

    /**
     * Save {@link #mLines} and {@link #mDumpName}.
     */
    @Override
    public void onSaveInstanceState (Bundle outState) {
        outState.putStringArray("lines", mLines);
        outState.putString("file_name", mDumpName);
    }

    /**
     * Handle the selected function from the editor menu.
     * @see #saveDump()
     * @see #shareDump()
     * @see #showAscii()
     * @see #showAC()
     * @see #decodeValueBlocks()
     * @see #openValueBlockTool()
     * @see #openAccessConditionTool()
     * @see #openBccTool()
     * @see #decodeDateOfManuf()
     * @see #writeDump()
     * @see #diffDump()
     * @see #saveKeys()
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection.
        switch (item.getItemId()) {
        case R.id.menuDumpEditorSave:
            saveDump();
            return true;
        case R.id.menuDumpEditorAscii:
            showAscii();
            return true;
        case R.id.menuDumpEditorAccessConditions:
            showAC();
            return true;
        case R.id.menuDumpEditorValueBlocksAsInt:
            decodeValueBlocks();
            return true;
        case R.id.menuDumpEditorShare:
            shareDump();
            return true;
        case R.id.menuDumpEditorOpenValueBlockTool:
            openValueBlockTool();
            return true;
        case R.id.menuDumpEditorOpenAccessConditionTool:
            openAccessConditionTool();
            return true;
        case R.id.menuDumpEditorOpenBccTool:
            openBccTool();
            return true;
        case R.id.menuDumpEditorDecodeDateOfManuf:
            decodeDateOfManuf();
            return true;
        case R.id.menuDumpEditorWriteDump:
            writeDump();
            return true;
        case R.id.menuDumpEditorDiffDump:
            diffDump();
            return true;
        case R.id.menuDumpEditorSaveKeys:
            saveKeys();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Update the coloring. This method updates the colors if all
     * data are valid {@link #checkDumpAndUpdateLines()}.
     * To do so, it re-initializes the whole editor... not quite beautiful.
     * @param view The View object that triggered the method
     * (in this case the update color text (color caption text)).
     * @see #checkDumpAndUpdateLines()
     * @see Common#isValidDumpErrorToast(int, Context)
     * @see #initEditor(String[])
     */
    public void onUpdateColors(View view) {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }
        // Backup focused view.
        View focused = mLayout.getFocusedChild();
        int focusIndex = -1;
        if (focused != null) {
            focusIndex = mLayout.indexOfChild(focused);
        }
        initEditor(mLines);
        if (focusIndex != -1) {
            // Restore focused view.
            while (focusIndex >= 0
                    && mLayout.getChildAt(focusIndex) == null) {
                focusIndex--;
            }
            if (focusIndex >= 0) {
                mLayout.getChildAt(focusIndex).requestFocus();
            }
        }
    }

    /**
     * Show a dialog in which the user can chose between "save", "don't save"
     * and "cancel", if there are unsaved changes.
     */
    @Override
    public void onBackPressed() {
        if (mDumpChanged) {
            new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_save_before_quitting_title)
            .setMessage(R.string.dialog_save_before_quitting)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton(R.string.action_save,
                    (dialog, which) -> {
                        // Save.
                        mCloseAfterSuccessfulSave = true;
                        saveDump();
                    })
            .setNeutralButton(R.string.action_cancel,
                    (dialog, which) -> {
                        // Cancel. Do nothing.
                    })
            .setNegativeButton(R.string.action_dont_save,
                    (dialog, id) -> {
                        // Don't save.
                        finish();
                    }).show();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Set the state of {@link #mDumpChanged} to false and close the
     * editor if {@link #mCloseAfterSuccessfulSave} is true (due to exiting
     * with unsaved changes) after a successful save process.
     */
    @Override
    public void onSaveSuccessful() {
        if (mCloseAfterSuccessfulSave) {
            finish();
        }
        mDumpChanged = false;
    }

    /**
     * Reset the state of {@link #mCloseAfterSuccessfulSave} to false if
     * there was an error (or if the user hit cancel) during the save process.
     */
    @Override
    public void onSaveFailure() {
        mCloseAfterSuccessfulSave = false;
    }

    /**
     * Check if it is a valid dump ({@link #checkDumpAndUpdateLines()}),
     * create a file name suggestion and call
     * {@link #saveFile(String[], String, boolean, int, int)}.
     * @see #checkDumpAndUpdateLines()
     * @see #saveFile(String[], String, boolean, int, int)
     */
    private void saveDump() {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }

        // Set a filename (UID + Date + Time) if there is none.
        if (mDumpName == null) {
            GregorianCalendar calendar = new GregorianCalendar();
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",
                    Locale.getDefault());
            fmt.setCalendar(calendar);
            String dateFormatted = fmt.format(calendar.getTime());
            mDumpName = "UID_" + mUID + "_" + dateFormatted;
        }

        saveFile(mLines, mDumpName, true, R.string.dialog_save_dump_title,
                R.string.dialog_save_dump);
    }

    /**
     * Check if the external storage is writable
     * {@link Common#isExternalStorageWritableErrorToast(Context)},
     * ask user for a save name and then call
     * {@link Common#checkFileExistenceAndSave(File, String[], boolean,
     * Context, IActivityThatReactsToSave)}.
     * This is a helper function for {@link #saveDump()}
     * and {@link #saveKeys()}.
     * @param data Data to save.
     * @param fileName Name of the file.
     * @param isDump True if data contains a dump. False if data contains keys.
     * @param titleId Resource ID for the title of the dialog.
     * @param messageId Resource ID for the message of the dialog.
     * @see Common#isExternalStorageWritableErrorToast(Context)
     * @see Common#checkFileExistenceAndSave(File, String[], boolean,
     * Context, IActivityThatReactsToSave)
     */
    private void saveFile(final String[] data, final String fileName,
                          final boolean isDump, int titleId, int messageId) {
        if (!Common.getPreferences().getBoolean(UseInternalStorage.toString(),
                false) && !Common.isExternalStorageWritableErrorToast(this)) {
            return;
        }
        String targetDir = (isDump) ? Common.DUMPS_DIR : Common.KEYS_DIR;
        final File path = Common.getFileFromStorage(
                Common.HOME_DIR +  "/" + targetDir);
        final Context context = this;
        final IActivityThatReactsToSave activity = this;

        // Ask user for filename.
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setLines(1);
        input.setHorizontallyScrolling(true);
        input.setText(fileName);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
            .setTitle(titleId)
            .setMessage(messageId)
            .setIcon(android.R.drawable.ic_menu_save)
            .setView(input)
            .setPositiveButton(R.string.action_save,
                    (dialog, whichButton) -> {
                        if (input.getText() != null
                                && !input.getText().toString().equals("")) {
                            File file = new File(path.getPath(),
                                    input.getText().toString());
                            Common.checkFileExistenceAndSave(file, data,
                                    isDump, context, activity);
                            if (isDump) {
                                mDumpName = file.getName();
                            } else {
                                mKeysName = file.getName();
                            }
                        } else {
                            // Empty name is not allowed.
                            Toast.makeText(context, R.string.info_empty_file_name,
                                    Toast.LENGTH_LONG).show();
                        }
                    })
            .setNegativeButton(R.string.action_cancel,
                    (dialog, whichButton) -> mCloseAfterSuccessfulSave = false).show();
        onUpdateColors(null);
    }

    /**
     * Check if all sectors contain valid data. If all blocks are O.K.
     * {@link #mLines} will be updated.
     * @return <ul>
     * <li>0 - All blocks are O.K.</li>
     * <li>1 - At least one sector has not 4 or 16 blocks (lines).</li>
     * <li>2 - At least one block has invalid characters (not hex or "-" as
     * marker for no key/no data).</li>
     * <li>3 - At least one block has not 16 byte (32 chars).</li>
     * </ul>
     * @see #mLines
     */
    private int checkDumpAndUpdateLines() {
        ArrayList<String> checkedLines = new ArrayList<>();
        for(int i = 0; i < mLayout.getChildCount(); i++) {
            View child = mLayout.getChildAt(i);
            if (child instanceof EditText) {
                String[] lines = ((EditText)child).getText().toString()
                        .split(System.getProperty("line.separator"));
                if (lines.length != 4 && lines.length != 16) {
                    // Not 4 or 16 lines.
                    return 1;
                }
                for (int j = 0; j < lines.length; j++) {
                    // Is hex or "-" == NO_KEY or NO_DATA.
                    if (!lines[j].matches("[0-9A-Fa-f-]+")) {
                        // Not pure hex.
                        return 2;
                    }
                    if (lines[j].length() != 32) {
                        // Not 32 chars per line.
                        return 3;
                    }
                    lines[j] = lines[j].toUpperCase(Locale.getDefault());
                    checkedLines.add(lines[j]);
                }
            } else if (child instanceof TextView) {
                TextView tv = (TextView) child;
                String tag = (String) tv.getTag();
                // Only save real headers (not the headers
                // of sectors with "no keys found or dead sector" error).
                if (tag != null && tag.equals("real_header")) {
                    // Mark headers (sectors) with "+"
                    checkedLines.add("+Sector: "
                            + tv.getText().toString().split(": ")[1]);
                }
            }
        }
        // Update mLines.
        mLines = checkedLines.toArray(new String[checkedLines.size()]);
        return 0;
    }

    /**
     * Initialize the editor with the given lines. If the lines do not contain
     * a valid dump, an error Toast will be shown and the Activity exits.
     * @param lines Block data and header (e.g. "sector: 0"). Minimum is one
     * Sector (5 Lines, 1 Header + 4 Hex block data).
     * @see Common#isValidDump(String[], boolean)
     * @see Common#isValidDumpErrorToast(int, Context)
     */
    private void initEditor(String[] lines) {
        int err = Common.isValidDump(lines, true);
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            Toast.makeText(this, R.string.info_editor_init_error,
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Parse dump and show it.
        boolean tmpDumpChanged = mDumpChanged;
        mLayout.removeAllViews();
        boolean isFirstBlock = false;
        EditText et = null;
        ArrayList<SpannableString> blocks =
                new ArrayList<>(4);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("+")) {
                // Line is a header.
                isFirstBlock = lines[i].endsWith(" 0");
                String sectorNumber = lines[i].split(": ")[1];
                // Add sector header (TextView).
                TextView tv = new TextView(this);
                tv.setTextColor(
                        getResources().getColor(R.color.blue));
                tv.setText(getString(R.string.text_sector) +
                        ": " + sectorNumber);
                mLayout.addView(tv);
                // Add sector data (EditText) if not at the end and if the
                // next line is not an error line ("*").
                if (i+1 != lines.length && !lines[i+1].startsWith("*")) {
                    // Add sector data (EditText).
                    et = new EditText(this);
                    et.setLayoutParams(new LayoutParams(
                            LayoutParams.WRAP_CONTENT,
                            LayoutParams.WRAP_CONTENT));
                    et.setInputType(et.getInputType()
                            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                            | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                    et.setTypeface(Typeface.MONOSPACE);
                    // Set text size of an EditText to the text size of
                    // a TextView. (getTextSize() returns
                    // pixels - unit is needed.)
                    et.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                            new TextView(this).getTextSize());
                    // Add a listener for changes to the text.
                    et.addTextChangedListener(new TextWatcher(){
                        @Override
                        public void afterTextChanged(Editable s) {
                            // Text was changed.
                            mDumpChanged = true;
                        }
                        @Override
                        public void beforeTextChanged(CharSequence s,
                                                      int start, int count, int after) {}
                        @Override
                        public void onTextChanged(CharSequence s,
                                                  int start, int before, int count) {}
                    });
                    mLayout.addView(et);
                    // Tag headers of real sectors (sectors containing
                    // data (EditText) and not errors ("*")).
                    tv.setTag("real_header");
                }
            } else if (lines[i].startsWith("*")){
                // Error Line: Line is a sector that could not be read.
                TextView tv = new TextView(this);
                tv.setTextColor(
                        getResources().getColor(R.color.red));
                tv.setText("   " +  getString(
                        R.string.text_no_key_io_error));
                tv.setTag("error");
                mLayout.addView(tv);
            } else {
                // Line is a block.
                if (i+1 == lines.length || lines[i+1].startsWith("+")) {
                    // Line is a sector trailer.
                    blocks.add(colorSectorTrailer(lines[i]));
                    // Add sector data to the EditText.
                    CharSequence text = "";
                    int j;
                    for (j = 0; j < blocks.size()-1; j++) {
                        text = TextUtils.concat(
                                text, blocks.get(j), "\n");
                    }
                    text = TextUtils.concat(text, blocks.get(j));
                    et.setText(text, BufferType.SPANNABLE);
                    blocks = new ArrayList<>(4);
                } else {
                    // Add data block.
                    blocks.add(colorDataBlock(lines[i], isFirstBlock));
                    isFirstBlock = false;
                }
            }
        }
        // Initialization of the editor is not a change.
        mDumpChanged = tmpDumpChanged;
    }

    /**
     * Display the the hex data as US-ASCII ({@link HexToAscii}).
     * @see HexToAscii
     * @see #checkDumpAndUpdateLines()
     * @see Common#isValidDumpErrorToast(int, Context)
     */
    private void showAscii() {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }
        // Get all data blocks (skip all Access Conditions).
        ArrayList<String> tmpDump = new ArrayList<>();
        for (int i = 0; i < mLines.length-1; i++) {
            if (i+1 != mLines.length
                    && !mLines[i+1].startsWith("+")) {
                tmpDump.add(mLines[i]);
            }
        }
        String[] dump = tmpDump.toArray(new String[tmpDump.size()]);

        Intent intent = new Intent(this, HexToAscii.class);
        intent.putExtra(EXTRA_DUMP, dump);
        startActivity(intent);
    }

    /**
     * Display the access conditions {@link AccessConditionDecoder}.
     * @see AccessConditionDecoder
     * @see #checkDumpAndUpdateLines()
     * @see Common#isValidDumpErrorToast(int, Context)
     */
    private void showAC() {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }
        // Get all Access Conditions (skip Data).
        ArrayList<String> tmpACs = new ArrayList<>();
        int lastSectorHeader = 0;
        for (int i = 0; i < mLines.length; i++) {
            if (mLines[i].startsWith("+")) {
                // Header.
                tmpACs.add(mLines[i]);
                lastSectorHeader = i;
            } else if (i+1 == mLines.length
                    || mLines[i+1].startsWith("+")) {
                // Access Condition.
                if (i - lastSectorHeader > 4) {
                    // Access Conditions of a sector
                    // with more than 4 blocks --> Mark ACs with "*".
                    tmpACs.add("*" + mLines[i].substring(12, 20));
                } else {
                    tmpACs.add(mLines[i].substring(12, 20));
                }
            }
        }
        String[] ac = tmpACs.toArray(new String[tmpACs.size()]);

        Intent intent = new Intent(this, AccessConditionDecoder.class);
        intent.putExtra(AccessConditionDecoder.EXTRA_AC, ac);
        startActivity(intent);
    }

    /**
     * Display the value blocks as integer ({@link ValueBlocksToInt}).
     * @see ValueBlocksToInt
     * @see #checkDumpAndUpdateLines()
     * @see Common#isValidDumpErrorToast(int, Context)
     */
    private void decodeValueBlocks() {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }

        // Get all Value Blocks (skip other blocks).
        ArrayList<String> tmpVBs = new ArrayList<>();
        String header = "";
        int blockCounter = 0;
        for (String line : mLines) {
            if (line.startsWith("+")) {
                header = line;
                blockCounter = 0;
            } else {
                if (Common.isValueBlock(line)) {
                    // Header.
                    tmpVBs.add(header + ", Block: " + blockCounter);
                    // Value Block.
                    tmpVBs.add(line);
                }
                blockCounter++;
            }
        }

        if (tmpVBs.size() > 0) {
            String[] vb = tmpVBs.toArray(new String[tmpVBs.size()]);
            Intent intent = new Intent(this, ValueBlocksToInt.class);
            intent.putExtra(ValueBlocksToInt.EXTRA_VB, vb);
            startActivity(intent);
        } else {
            // No value blocks found.
            Toast.makeText(this, R.string.info_no_vb_in_dump,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Decode the date of manufacture (using the last 2 bytes of the
     * manufacturer block) and display the result as dialog.
     * Both of this bytes must be in BCD format (only digits, no letters).
     * The first byte (week of manufacture) must be between 1 and 53.
     * The second byte (year of manufacture) must be between 0 and
     * the current year.
     */
    private void decodeDateOfManuf() {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }
        if (mLines[0].equals("+Sector: 0") && !mLines[1].contains("-")) {
            int year;
            int week;
            SimpleDateFormat sdf = new SimpleDateFormat(
                    "yy", Locale.getDefault());
            CharSequence styledText;
            try {
                year = Integer.parseInt(mLines[1].substring(30, 32));
                week = Integer.parseInt(mLines[1].substring(28, 30));
                int now = Integer.parseInt(sdf.format(new Date()));
                if (year >= 0 && year <= now && week >= 1 && week <= 53) {
                    // Calculate the date of manufacture.
                    Calendar calendar = Calendar.getInstance();
                    calendar.clear();
                    calendar.set(Calendar.WEEK_OF_YEAR, week);
                    // year + 2000: Yep, hardcoded. Hopefully MFC is dead
                    // around year 3000. :)
                    calendar.set(Calendar.YEAR, year + 2000);
                    sdf.applyPattern("dd.MM.yyyy");
                    String startDate = sdf.format(calendar.getTime());
                    calendar.add(Calendar.DATE, 6);
                    String endDate = sdf.format(calendar.getTime());

                    styledText = Html.fromHtml(getString(
                            R.string.dialog_date_of_manuf,
                            startDate, endDate));
                } else {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException ex) {
                // Error. Tag has wrong data set as date of manufacture.
                styledText = getText(R.string.dialog_date_of_manuf_error);
            }
            // Show dialog.
            new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_date_of_manuf_title)
                .setMessage(styledText)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(R.string.action_ok,
                        (dialog, which) -> {
                            // Do nothing.
                        }).show();
        } else {
            // Error. There is no block 0.
            Toast.makeText(this, R.string.info_block0_missing,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Open the Value Block decoder/encoder ({@link ValueBlockTool}).
     * @see ValueBlockTool
     */
    private void openValueBlockTool() {
        Intent intent = new Intent(this, ValueBlockTool.class);
        startActivity(intent);
    }

    /**
     * Open the Access Condition decoder/encoder ({@link AccessConditionTool}).
     * @see AccessConditionTool
     */
    private void openAccessConditionTool() {
        Intent intent = new Intent(this, AccessConditionTool.class);
        startActivity(intent);
    }

    /**
     * Open the BCC calculator ({@link BccTool}).
     * @see BccTool
     */
    private void openBccTool() {
        Intent intent = new Intent(this, BccTool.class);
        startActivity(intent);
    }

    /**
     * Write the currently displayed dump.
     * @see WriteTag
     */
    private void writeDump() {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }
        Intent intent = new Intent(this, WriteTag.class);
        intent.putExtra(WriteTag.EXTRA_DUMP, mLines);
        startActivity(intent);
    }

    /**
     * Compare the currently displayed dump with another dump using
     * the {@link DiffTool}.
     * @see DiffTool
     */
    private void diffDump() {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }
        Intent intent = new Intent(this, DiffTool.class);
        intent.putExtra(DiffTool.EXTRA_DUMP, mLines);
        startActivity(intent);
    }


    /**
     * Share a dump as "file://" stream resource (e.g. as e-mail attachment).
     * The dump will be checked and stored in the {@link Common#TMP_DIR}
     * directory. After this, a dialog will be displayed in which the user
     * can choose between apps that are willing to handle the dump.
     * @see Common#TMP_DIR
     */
    private void shareDump() {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }
        // Save dump to to a temporary file which will be
        // attached for sharing (and stored in the tmp folder).
        String fileName;
        if (mDumpName == null) {
            // The dump has no name. Use date and time as name.
            GregorianCalendar calendar = new GregorianCalendar();
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",
                    Locale.getDefault());
            fmt.setCalendar(calendar);
            fileName = fmt.format(calendar.getTime());
        } else {
            fileName = mDumpName;
        }
        // Save file to tmp directory.
        File file = Common.getFileFromStorage(Common.HOME_DIR + "/" +
                Common.TMP_DIR + "/" + fileName);
        if (!Common.saveFile(file, mLines, false)) {
            Toast.makeText(this, R.string.info_save_error,
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Share file.
        Common.shareTmpFile(this, file);
    }

    /**
     * Check if it is a valid dump ({@link #checkDumpAndUpdateLines()}),
     * extract all keys from the current dump, create a file name suggestion
     * and call {@link #saveFile(String[], String, boolean, int, int)}.
     * @see #checkDumpAndUpdateLines()
     * @see #saveFile(String[], String, boolean, int, int)
     */
    private void saveKeys() {
        int err = checkDumpAndUpdateLines();
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            return;
        }

        // Get all keys (skip Data and ACs).
        HashSet<String> tmpKeys = new HashSet<>();
        for (int i = 0; i < mLines.length; i++) {
           if (i+1 == mLines.length || mLines[i+1].startsWith("+")) {
                // Sector trailer.
               String keyA = mLines[i].substring(0,12).toUpperCase();
               String keyB = mLines[i].substring(20).toUpperCase();
               if (!keyA.equals(MCReader.NO_KEY)) {
                   tmpKeys.add(keyA);
               }
               if (!keyB.equals(MCReader.NO_KEY)) {
                   tmpKeys.add(keyB);
               }
            }
        }
        String[] keys = tmpKeys.toArray(new String[tmpKeys.size()]);

        // Set the filename to the UID if there is none.
        if (mKeysName == null) {
            if (mDumpName == null) {
                mKeysName = "UID_" + mUID;
            } else {
                mKeysName = new String(mDumpName);
            }
        }

        saveFile(keys, mKeysName, false, R.string.dialog_save_keys_title,
                R.string.dialog_save_keys);
    }

    /**
     * Create a full colored string (representing one block).
     * @param data Block data as hex string (16 Byte, 32 Chars.).
     * @param hasUID True if the block is the first block of the entire tag
     * (Sector 0, Block 0).
     * @return A full colored string.
     */
    private SpannableString colorDataBlock(String data, boolean hasUID) {
        SpannableString ret;
        if (hasUID) {
            // First block (UID, manuf. data).
            ret = new SpannableString(TextUtils.concat(
                    Common.colorString(data,
                            getResources().getColor(R.color.purple))));
        } else {
            if (Common.isValueBlock(data)) {
                // Value block.
                ret = Common.colorString(data,
                        getResources().getColor(R.color.yellow));
            } else {
                // Just data.
                ret = new SpannableString(data);
            }
        }
        return ret;
    }

    /**
     * Create a full colored sector trailer (representing the last block of
     * every sector).
     * @param data Block data as hex string (16 Byte, 32 Chars.).
     * @return A full colored string.
     */
    private SpannableString colorSectorTrailer(String data) {
        // Get sector trailer colors.
        int colorKeyA = getResources().getColor(
                R.color.light_green);
        int colorKeyB = getResources().getColor(
                R.color.dark_green);
        int colorAC = getResources().getColor(
                R.color.orange);
        try {
            SpannableString keyA = Common.colorString(
                    data.substring(0, 12), colorKeyA);
            SpannableString keyB = Common.colorString(
                    data.substring(20), colorKeyB);
            SpannableString ac = Common.colorString(
                    data.substring(12, 20), colorAC);
            return new SpannableString(
                    TextUtils.concat(keyA, ac, keyB));
        } catch (IndexOutOfBoundsException e) {
            Log.d(LOG_TAG, "Error while coloring " +
                    "sector trailer");
        }
        return new SpannableString(data);
    }
}
