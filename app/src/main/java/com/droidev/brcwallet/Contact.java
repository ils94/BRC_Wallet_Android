package com.droidev.brcwallet;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public class Contact {
    public String name;
    public String address;

    public Contact(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("name", name);
        j.put("address", address);
        return j;
    }

    public static Contact fromJson(JSONObject j) throws JSONException {
        return new Contact(j.getString("name"), j.getString("address"));
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }
}