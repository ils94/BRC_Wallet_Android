package com.droidev.brcwallet;

import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;

public class ContactsActivity extends AppCompatActivity
        implements ContactAdapter.ContactActionListener, DialogManager.ScannerCallback {

    private ContactsStore contactsStore;
    private List<Contact> contacts;
    private ContactAdapter adapter;
    private DialogManager dialogManager;
    private WalletOperations operations;
    private EditText tempEdtAddress;

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null && tempEdtAddress != null) {
                    tempEdtAddress.setText(result.getContents());
                    tempEdtAddress = null;
                }
            });

    private final ActivityResultLauncher<String[]> openDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importContactsFromUri(uri);
                }
            });

    private final ActivityResultLauncher<String> createDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri != null) {
                    saveContactsToUri(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);

        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        contactsStore = new ContactsStore(this);
        contacts = contactsStore.loadContacts();

        RecyclerView recyclerView = findViewById(R.id.recyclerContacts);
        adapter = new ContactAdapter(contacts, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        updateEmptyVisibility();

        WalletStore walletStore = new WalletStore(this);
        BRCApi api = new BRCApi(walletStore.getApiBase());
        operations = new WalletOperations(this, walletStore, api);

        DialogManager.ContactActionCallback contactCallback = new DialogManager.ContactActionCallback() {
            @Override
            public void onContactAdded(String name, String address) {
                boolean added = contactsStore.addContact(new Contact(name, address));
                if (added) {
                    contacts = contactsStore.loadContacts();
                    adapter.updateList(contacts);
                    updateEmptyVisibility();
                } else {
                    Toast.makeText(ContactsActivity.this,
                            R.string.toast_contact_duplicate, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onContactEdited(int position, String name, String address) {
                boolean updated = contactsStore.updateContact(position, new Contact(name, address));
                if (updated) {
                    contacts = contactsStore.loadContacts();
                    adapter.updateList(contacts);
                    updateEmptyVisibility();
                } else {
                    Toast.makeText(ContactsActivity.this,
                            R.string.toast_contact_duplicate, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onContactDeleted(int position) {
                contactsStore.deleteContact(position);
                contacts = contactsStore.loadContacts();
                adapter.updateList(contacts);
                updateEmptyVisibility();
            }
        };

        dialogManager = new DialogManager(this, walletStore, new DialogManager.WalletActionCallback() {
            @Override public void onWalletCreated() {}
            @Override public void onWalletImported() {}
            @Override public void onWalletExported(byte[] privKey) {}
            @Override public void onServerChanged(String newUrl) {}
            @Override public void onHeightSet(long height) {}

            @Override
            public void onHistoryRescanRequested(long height) {

            }

            @Override
            public void onSendRequested(byte[] to, long amountWei, long feeWei, String password) {
                operations.sendTransaction(to, amountWei, feeWei, password, (success, message) -> Toast.makeText(ContactsActivity.this, message, Toast.LENGTH_LONG).show());
            }
        });

        dialogManager.setContactCallback(contactCallback);
        dialogManager.setScannerCallback(this);
    }

    @Override
    public void startScan(EditText targetField) {
        tempEdtAddress = targetField;
        ScanOptions options = new ScanOptions();
        options.setPrompt(getString(R.string.scan_prompt));
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setBarcodeImageEnabled(false);
        scanLauncher.launch(options);
    }

    private void updateEmptyVisibility() {
        TextView txtEmpty = findViewById(R.id.txtEmptyContacts);
        RecyclerView recyclerView = findViewById(R.id.recyclerContacts);

        if (contacts.isEmpty()) {
            txtEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            txtEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.contacts_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_add_contact) {
            dialogManager.showAddContactDialog();
            return true;
        } else if (id == R.id.action_export_contacts) {
            createDocumentLauncher.launch("contacts.json");
            return true;
        } else if (id == R.id.action_import_contacts) {
            dialogManager.showImportContactsDialog(() ->
                    openDocumentLauncher.launch(new String[]{"application/json", "text/plain"})
            );
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void saveContactsToUri(Uri uri) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Contact c : contacts) {
                jsonArray.put(c.toJson());
            }

            String json = jsonArray.toString(2);
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) {
                Toast.makeText(this, R.string.toast_export_failed, Toast.LENGTH_LONG).show();
                return;
            }

            os.write(json.getBytes());
            os.flush();
            os.close();

            Toast.makeText(this, R.string.toast_export_success, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_export_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void importContactsFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }

            reader.close();
            assert is != null;
            is.close();

            processContactsJson(sb.toString().trim());
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_import_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void processContactsJson(String jsonText) {
        try {
            JSONArray arr = new JSONArray(jsonText);
            int importedCount = 0;
            int duplicateCount = 0;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = obj.getString("name");
                String address = obj.getString("address");

                if (name.isEmpty() || address.isEmpty()) continue;
                if (!Bip39Helper.isHexPrivateKey(address) || address.length() != 64) continue;

                Contact contact = new Contact(name, address);
                if (contactsStore.addContact(contact)) {
                    importedCount++;
                } else {
                    duplicateCount++;
                }
            }

            contacts = contactsStore.loadContacts();
            adapter.updateList(contacts);
            updateEmptyVisibility();

            String message = getString(R.string.toast_import_result, importedCount, duplicateCount);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_import_invalid_json, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onSend(Contact contact) {
        dialogManager.showSendDialog(contact.address);
    }

    @Override
    public void onEdit(Contact contact) {
        int position = contacts.indexOf(contact);
        if (position >= 0) {
            dialogManager.showEditContactDialog(contact, position);
        }
    }

    @Override
    public void onDelete(Contact contact) {
        int position = contacts.indexOf(contact);
        if (position >= 0) {
            dialogManager.showDeleteContactDialog(contact, position);
        }
    }
}