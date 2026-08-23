package com.droidev.brcwallet;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class ContactsStore {

    private static final String PREFS = "contacts_prefs";
    private static final String KEY_CONTACTS = "contacts";

    private final SharedPreferences prefs;

    public ContactsStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Contact> loadContacts() {
        List<Contact> list = new ArrayList<>();
        String json = prefs.getString(KEY_CONTACTS, null);
        if (json == null) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(Contact.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public void saveContacts(List<Contact> contacts) {
        try {
            JSONArray arr = new JSONArray();
            for (Contact c : contacts) {
                arr.put(c.toJson());
            }
            prefs.edit().putString(KEY_CONTACTS, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isDuplicate(Contact newContact, int excludeIndex) {
        List<Contact> contacts = loadContacts();
        for (int i = 0; i < contacts.size(); i++) {
            if (i == excludeIndex) continue;
            Contact existing = contacts.get(i);
            if (existing.name.equalsIgnoreCase(newContact.name) ||
                    existing.address.equalsIgnoreCase(newContact.address)) {
                return true;
            }
        }
        return false;
    }

    public boolean addContact(Contact contact) {
        if (isDuplicate(contact, -1)) {
            return false;
        }
        List<Contact> contacts = loadContacts();
        contacts.add(contact);
        saveContacts(contacts);
        return true;
    }


    public boolean updateContact(int index, Contact newContact) {
        if (isDuplicate(newContact, index)) {
            return false;
        }
        List<Contact> contacts = loadContacts();
        if (index >= 0 && index < contacts.size()) {
            contacts.set(index, newContact);
            saveContacts(contacts);
            return true;
        }
        return false;
    }

    public void deleteContact(int index) {
        List<Contact> contacts = loadContacts();
        if (index >= 0 && index < contacts.size()) {
            contacts.remove(index);
            saveContacts(contacts);
        }
    }
}