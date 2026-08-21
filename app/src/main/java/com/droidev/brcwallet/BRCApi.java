package com.droidev.brcwallet;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public final class BRCApi {

    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private String baseUrl;

    public BRCApi(String baseUrl) {
        setBaseUrl(baseUrl);
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        this.baseUrl = baseUrl;
    }

    public static final class Tip {
        public final long height;
        public final String tipHash;

        Tip(long h, String t) {
            height = h;
            tipHash = t;
        }
    }

    public Tip getTip() throws IOException {
        try (Response r = http.newCall(new Request.Builder().url(baseUrl + "/tip").build()).execute()) {
            if (!r.isSuccessful()) throw new IOException("GET /tip HTTP " + r.code());
            assert r.body() != null;
            JSONObject j = new JSONObject(r.body().string());
            return new Tip(j.getLong("height"), j.getString("tipHash"));
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    public List<byte[]> getBlocks(long fromHeight, int max) throws IOException {
        String url = baseUrl + "/blocks?fromHeight=" + fromHeight + "&max=" + max;
        try (Response r = http.newCall(new Request.Builder().url(url).build()).execute()) {
            if (!r.isSuccessful()) throw new IOException("GET /blocks HTTP " + r.code());
            assert r.body() != null;
            JSONObject j = new JSONObject(r.body().string());
            JSONArray arr = j.getJSONArray("blocks");
            List<byte[]> out = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) out.add(TxBuilder.fromHex(arr.getString(i)));
            return out;
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    public static final class SubmitResult {
        public final int admitted;
        public final List<String> errors;

        SubmitResult(int a, List<String> e) {
            admitted = a;
            errors = e;
        }

        public boolean ok() {
            return admitted > 0;
        }
    }

    public SubmitResult submitTxs(List<String> txHexList) throws IOException {
        try {
            JSONArray txs = new JSONArray();
            for (String h : txHexList) txs.put(h);
            String body = new JSONObject().put("txs", txs).toString();
            try (Response r = http.newCall(new Request.Builder()
                    .url(baseUrl + "/txs")
                    .post(RequestBody.create(body, JSON))
                    .build()).execute()) {
                if (!r.isSuccessful()) throw new IOException("POST /txs HTTP " + r.code());
                assert r.body() != null;
                JSONObject j = new JSONObject(r.body().string());
                List<String> errs = new ArrayList<>();
                JSONArray ea = j.optJSONArray("errors");
                if (ea != null) for (int i = 0; i < ea.length(); i++) errs.add(ea.getString(i));
                return new SubmitResult(j.getInt("admitted"), errs);
            }
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }
}
