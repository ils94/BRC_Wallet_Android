package com.droidev.brcwallet;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class HistoryDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "brc_history.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE = "tx_history";

    private static final String COL_ID = "id";
    private static final String COL_TYPE = "type";
    private static final String COL_HEIGHT = "height";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final String COL_TXID = "txid";
    private static final String COL_FROM = "from_addr";
    private static final String COL_TO = "to_addr";
    private static final String COL_AMOUNT = "amount";
    private static final String COL_FEE = "fee";
    private static final String COL_NONCE = "nonce";
    private static final String COL_DEDUP = "dedup_key";

    public HistoryDatabase(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE + " (" +
                        COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_TYPE + " TEXT NOT NULL, " +
                        COL_HEIGHT + " INTEGER NOT NULL, " +
                        COL_TIMESTAMP + " INTEGER NOT NULL DEFAULT 0, " +
                        COL_TXID + " TEXT NOT NULL DEFAULT '', " +
                        COL_FROM + " TEXT NOT NULL DEFAULT '', " +
                        COL_TO + " TEXT NOT NULL DEFAULT '', " +
                        COL_AMOUNT + " INTEGER NOT NULL, " +
                        COL_FEE + " INTEGER NOT NULL DEFAULT 0, " +
                        COL_NONCE + " INTEGER NOT NULL DEFAULT 0, " +
                        COL_DEDUP + " TEXT NOT NULL UNIQUE" +
                        ")"
        );
        db.execSQL("CREATE INDEX idx_tx_height ON " + TABLE + " (" + COL_HEIGHT + ")");
        db.execSQL("CREATE INDEX idx_tx_type ON " + TABLE + " (" + COL_TYPE + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    static String dedupKey(TxRecord r) {
        if (r.txid != null && !r.txid.isEmpty()) {
            return r.type.name() + "|" + r.txid;
        }
        return r.type.name() + "|" + r.blockHeight + "|" + r.amountWei;
    }

    public void insertAll(List<TxRecord> records) {
        if (records == null || records.isEmpty()) return;

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (TxRecord r : records) {
                ContentValues cv = new ContentValues();
                cv.put(COL_TYPE, r.type.name());
                cv.put(COL_HEIGHT, r.blockHeight);
                cv.put(COL_TIMESTAMP, r.timestamp);
                cv.put(COL_TXID, r.txid == null ? "" : r.txid);
                cv.put(COL_FROM, r.from == null ? "" : r.from);
                cv.put(COL_TO, r.to == null ? "" : r.to);
                cv.put(COL_AMOUNT, r.amountWei);
                cv.put(COL_FEE, r.feeWei);
                cv.put(COL_NONCE, r.nonce);
                cv.put(COL_DEDUP, dedupKey(r));
                db.insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<TxRecord> loadAll() {
        List<TxRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor c = db.query(
                TABLE,
                null,
                null,
                null,
                null,
                null,
                COL_HEIGHT + " ASC, " + COL_ID + " ASC"
        )) {
            int iType = c.getColumnIndexOrThrow(COL_TYPE);
            int iHeight = c.getColumnIndexOrThrow(COL_HEIGHT);
            int iTs = c.getColumnIndexOrThrow(COL_TIMESTAMP);
            int iTxid = c.getColumnIndexOrThrow(COL_TXID);
            int iFrom = c.getColumnIndexOrThrow(COL_FROM);
            int iTo = c.getColumnIndexOrThrow(COL_TO);
            int iAmount = c.getColumnIndexOrThrow(COL_AMOUNT);
            int iFee = c.getColumnIndexOrThrow(COL_FEE);
            int iNonce = c.getColumnIndexOrThrow(COL_NONCE);

            while (c.moveToNext()) {
                TxRecord.Type type;
                try {
                    type = TxRecord.Type.valueOf(c.getString(iType));
                } catch (Exception e) {
                    continue;
                }
                list.add(new TxRecord(
                        type,
                        c.getLong(iHeight),
                        c.getLong(iTs),
                        c.getString(iTxid),
                        c.getString(iFrom),
                        c.getString(iTo),
                        c.getLong(iAmount),
                        c.getLong(iFee),
                        c.getLong(iNonce)
                ));
            }
        }
        return list;
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE, null, null);
    }
}