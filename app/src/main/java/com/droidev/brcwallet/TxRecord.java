package com.droidev.brcwallet;

import org.json.JSONException;
import org.json.JSONObject;

public class TxRecord {

    public enum Type { SEND, RECEIVE, MINE, LOCK, REDEEM }

    public final Type type;
    public final long blockHeight;
    public final String txid;
    public final String from;
    public final String to;
    public final long amountWei;
    public final long feeWei;
    public final long nonce;

    public TxRecord(Type type, long blockHeight, String txid,
                    String from, String to,
                    long amountWei, long feeWei, long nonce) {
        this.type = type;
        this.blockHeight = blockHeight;
        this.txid = txid;
        this.from = from;
        this.to = to;
        this.amountWei = amountWei;
        this.feeWei = feeWei;
        this.nonce = nonce;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("type", type.name());
        j.put("height", blockHeight);
        j.put("txid", txid == null ? "" : txid);
        j.put("from", from);
        j.put("to", to);
        j.put("amount", amountWei);
        j.put("fee", feeWei);
        j.put("nonce", nonce);
        return j;
    }

    public static TxRecord fromJson(JSONObject j) throws JSONException {
        Type t = Type.valueOf(j.getString("type"));
        return new TxRecord(
                t,
                j.getLong("height"),
                j.optString("txid", ""),
                j.getString("from"),
                j.getString("to"),
                j.getLong("amount"),
                j.getLong("fee"),
                j.getLong("nonce")
        );
    }
}