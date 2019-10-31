package ru.vladlin.rfid_android_mifare;

import android.os.Bundle;
import android.util.Log;
import android.widget.TableLayout;
import android.widget.TableLayout.LayoutParams;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import java.nio.ByteBuffer;

public class ValueBlocksToInt extends BasicActivity {

    public final static String EXTRA_VB =
            "de.syss.MifareClassicTool.Activity.VB";

    private static final String LOG_TAG =
            ValueBlocksToInt.class.getSimpleName();

    private TableLayout mLayout;

    /**
     * Get value blocks from Intent and initialize Activity to
     * displaying them. If there is no Intent with
     * {@link #EXTRA_VB}, the Activity will be exited.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_value_blocks_to_int);

        boolean noValueBlocks = false;
        if (getIntent().hasExtra(EXTRA_VB)) {
            mLayout = findViewById(
                    R.id.tableLayoutValueBlocksToInt);
            String[] valueBlocks = getIntent().getStringArrayExtra(EXTRA_VB);
            if (valueBlocks.length > 0) {
                for (int i = 0; i < valueBlocks.length; i=i+2) {
                    String[] sectorAndBlock = valueBlocks[i].split(", ");
                    String sectorNumber = sectorAndBlock[0].split(": ")[1];
                    String blockNumber = sectorAndBlock[1].split(": ")[1];
                    addPosInfoRow(getString(R.string.text_sector)
                            + ": " + sectorNumber + ", "
                            + getString(R.string.text_block)
                            + ": " + blockNumber);
                    addValueBlock(valueBlocks[i+1]);
                }
            } else {
                noValueBlocks = true;
            }
        } else {
            noValueBlocks = true;
        }

        if (noValueBlocks) {
            Log.d(LOG_TAG, "There was no value block in intent.");
            finish();
        }
    }

    /**
     * Add a row with position information to the layout table.
     * This row shows the user where the value block is located (sector, block).
     * @param value The position information (e.g. "Sector: 1, Block: 2").
     */
    private void addPosInfoRow(String value) {
        TextView header = new TextView(this);
        header.setText(Common.colorString(value,
                getResources().getColor(R.color.blue)),
                BufferType.SPANNABLE);
        TableRow tr = new TableRow(this);
        tr.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        tr.addView(header);
        mLayout.addView(tr, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
    }

    /**
     * Add full value block information (original
     * and integer format) to the layout table (two rows).
     * @param hexValueBlock The value block as hex string (32 chars.).
     */
    private void addValueBlock(String hexValueBlock) {
        TableRow tr = new TableRow(this);
        TextView what = new TextView(this);
        TextView value = new TextView(this);

        // Original.
        tr.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        what.setText(R.string.text_vb_orig);
        value.setText(Common.colorString(hexValueBlock.substring(0, 8),
                getResources().getColor(R.color.yellow)));
        tr.addView(what);
        tr.addView(value);
        mLayout.addView(tr, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        // Resolved to int.
        tr = new TableRow(this);
        tr.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        what = new TextView(this);
        what.setText(R.string.text_vb_as_int_decoded);
        value = new TextView(this);
        byte[] asBytes = Common.hexStringToByteArray(
                hexValueBlock.substring(0, 8));
        Common.reverseByteArrayInPlace(asBytes);
        ByteBuffer bb = ByteBuffer.wrap(asBytes);
        int i = bb.getInt();
        String asInt = "" + i;
        value.setText(Common.colorString(asInt,
                getResources().getColor(R.color.light_green)));
        tr.addView(what);
        tr.addView(value);
        mLayout.addView(tr, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
    }
}
