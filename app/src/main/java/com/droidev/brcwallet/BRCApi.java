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

    private List<String> baseUrls;
    private int currentIndex;

    public BRCApi(String baseUrl) {
        setBaseUrls(baseUrl);
    }

    public void setBaseUrl(String baseUrl) {
        setBaseUrls(baseUrl);
    }

    public void setBaseUrls(String urls) {
        if (urls == null || urls.trim().isEmpty()) {
            urls = "http://10.0.2.2:9000";
        }

        String[] parts = urls.split(",");
        List<String> normalized = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            normalized.add(trimmed);
        }

        if (normalized.isEmpty()) {
            normalized.add("http://10.0.2.2:9000");
        }

        this.baseUrls = normalized;
        this.currentIndex = 0;
    }

    public String getCurrentBaseUrl() {
        return baseUrls.get(currentIndex);
    }

    private void nextBaseUrl() {
        currentIndex = (currentIndex + 1) % baseUrls.size();
    }

    private void resetBaseUrl() {
        currentIndex = 0;
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
        int attempts = 0;
        IOException lastError = null;
        while (attempts < baseUrls.size()) {
            String url = getCurrentBaseUrl();
            try {
                return getTipFrom(url);
            } catch (IOException e) {
                lastError = e;
                nextBaseUrl();
                attempts++;
            }
        }
        resetBaseUrl();
        throw lastError != null ? lastError : new IOException("All servers failed");
    }

    private Tip getTipFrom(String baseUrl) throws IOException {
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
        int attempts = 0;
        IOException lastError = null;
        while (attempts < baseUrls.size()) {
            String url = getCurrentBaseUrl();
            try {
                return getBlocksFrom(url, fromHeight, max);
            } catch (IOException e) {
                lastError = e;
                nextBaseUrl();
                attempts++;
            }
        }
        resetBaseUrl();
        throw lastError != null ? lastError : new IOException("All servers failed");
    }

    private List<byte[]> getBlocksFrom(String baseUrl, long fromHeight, int max) throws IOException {
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
        int attempts = 0;
        IOException lastError = null;
        while (attempts < baseUrls.size()) {
            String url = getCurrentBaseUrl();
            try {
                return submitTxsTo(url, txHexList);
            } catch (IOException e) {
                lastError = e;
                nextBaseUrl();
                attempts++;
            }
        }
        resetBaseUrl();
        throw lastError != null ? lastError : new IOException("All servers failed");
    }

    private SubmitResult submitTxsTo(String baseUrl, List<String> txHexList) throws IOException {
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