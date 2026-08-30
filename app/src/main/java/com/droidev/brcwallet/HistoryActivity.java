package com.droidev.brcwallet;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private List<TxRecord> allTransactions;
    private TransactionAdapter adapter;
    private WalletStore store;
    private ContactsStore contactsStore;

    private AutoCompleteTextView autoCompleteContact;
    private ImageButton btnClearContactFilter;
    private TextView txtHistoryBalance;
    private TextView txtHistoryCount;

    private String contactFilterAddress = null;
    private String searchQuery = null;

    private Handler uiHandler;
    private Runnable updater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        RecyclerView recyclerView = findViewById(R.id.recyclerHistory);
        Spinner spinnerSort = findViewById(R.id.spinnerSort);
        Spinner spinnerFilter = findViewById(R.id.spinnerFilter);
        autoCompleteContact = findViewById(R.id.autoCompleteContact);
        btnClearContactFilter = findViewById(R.id.btnClearContactFilter);
        txtHistoryBalance = findViewById(R.id.txtHistoryBalance);
        txtHistoryCount = findViewById(R.id.txtHistoryCount);

        store = new WalletStore(this);
        allTransactions = store.loadHistory();
        contactsStore = new ContactsStore(this);

        byte[] pub = store.loadPublicKey();
        String myAddress = pub != null ? TxBuilder.toHex(pub) : "";

        adapter = new TransactionAdapter(new ArrayList<>(), contactsStore, myAddress);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        List<Contact> contactList = contactsStore.loadContacts();
        ArrayAdapter<Contact> contactAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, contactList);
        autoCompleteContact.setAdapter(contactAdapter);
        autoCompleteContact.setThreshold(1);

        autoCompleteContact.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String typed = s.toString().trim();
                if (typed.isEmpty()) {
                    searchQuery = null;
                    contactFilterAddress = null;
                    btnClearContactFilter.setVisibility(View.GONE);
                } else {
                    searchQuery = typed;
                    Contact matched = findContactByNameOrAddress(typed);
                    if (matched != null) {
                        contactFilterAddress = matched.address;
                    } else {
                        contactFilterAddress = null;
                    }
                    btnClearContactFilter.setVisibility(View.VISIBLE);
                }
                applyFiltersAndSort();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        autoCompleteContact.setOnItemClickListener((parent, view, position, id) -> {
            Contact selected = contactAdapter.getItem(position);
            if (selected != null) {
                searchQuery = selected.name;
                contactFilterAddress = selected.address;
                btnClearContactFilter.setVisibility(View.VISIBLE);
                applyFiltersAndSort();
            }
        });

        btnClearContactFilter.setOnClickListener(v -> {
            autoCompleteContact.setText("");
            searchQuery = null;
            contactFilterAddress = null;
            btnClearContactFilter.setVisibility(View.GONE);
            applyFiltersAndSort();
        });

        ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.sort_options,
                android.R.layout.simple_spinner_item
        );
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSort.setAdapter(sortAdapter);

        ArrayAdapter<CharSequence> filterAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.filter_options,
                android.R.layout.simple_spinner_item
        );
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(filterAdapter);

        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFiltersAndSort();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFiltersAndSort();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        updateSummary();
        applyFiltersAndSort();

        uiHandler = new Handler(Looper.getMainLooper());
        updater = new Runnable() {
            @Override
            public void run() {
                updateBlockSubtitle();
                allTransactions = store.loadHistory();
                updateSummary();
                applyFiltersAndSort();
                uiHandler.postDelayed(this, 1000);
            }
        };
        uiHandler.post(updater);
    }

    private void updateSummary() {
        String balance = TxBuilder.weiToBrc(store.getBalanceWei());
        txtHistoryBalance.setText(getString(R.string.history_balance, balance));
    }

    private Contact findContactByNameOrAddress(String query) {
        String lowerQuery = query.toLowerCase();
        for (Contact c : contactsStore.loadContacts()) {
            if (c.name.toLowerCase().contains(lowerQuery)
                    || c.address.toLowerCase().contains(lowerQuery)) {
                return c;
            }
        }
        return null;
    }

    private void updateBlockSubtitle() {
        if (getSupportActionBar() != null) {
            long syncHeight = store.getSyncHeight();
            if (syncHeight >= 0) {
                getSupportActionBar().setSubtitle(
                        getString(R.string.current_block_subtitle, syncHeight)
                );
            } else {
                getSupportActionBar().setSubtitle(null);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (uiHandler != null && updater != null) {
            uiHandler.removeCallbacks(updater);
        }
    }

    private void applyFiltersAndSort() {
        Spinner spinnerSort = findViewById(R.id.spinnerSort);
        Spinner spinnerFilter = findViewById(R.id.spinnerFilter);

        int sortPos = spinnerSort.getSelectedItemPosition();
        int filterPos = spinnerFilter.getSelectedItemPosition();

        List<TxRecord> filtered = new ArrayList<>();

        for (TxRecord tx : allTransactions) {
            boolean include;
            switch (filterPos) {
                case 1:
                    include = (tx.type == TxRecord.Type.SEND);
                    break;
                case 2:
                    include = (tx.type == TxRecord.Type.RECEIVE);
                    break;
                case 3:
                    include = (tx.type == TxRecord.Type.MINE);
                    break;
                case 4:
                    include = (tx.type == TxRecord.Type.LOCK);
                    break;
                case 5:
                    include = (tx.type == TxRecord.Type.REDEEM);
                    break;
                default:
                    include = true;
                    break;
            }

            if (include && contactFilterAddress != null) {
                include = tx.from.equalsIgnoreCase(contactFilterAddress)
                        || tx.to.equalsIgnoreCase(contactFilterAddress);
            } else if (include && searchQuery != null && !searchQuery.isEmpty()) {
                String q = searchQuery.toLowerCase();
                String txid = tx.txid != null ? tx.txid.toLowerCase() : "";
                String from = tx.from != null ? tx.from.toLowerCase() : "";
                String to = tx.to != null ? tx.to.toLowerCase() : "";
                include = txid.contains(q) || from.contains(q) || to.contains(q);
            }

            if (include) {
                filtered.add(tx);
            }
        }

        Comparator<TxRecord> comparator;
        switch (sortPos) {
            case 1:
                comparator = Comparator.comparingLong(a -> a.blockHeight);
                break;
            case 2:
                comparator = (a, b) -> Long.compare(b.amountWei, a.amountWei);
                break;
            case 3:
                comparator = Comparator.comparingLong(a -> a.amountWei);
                break;
            default:
                comparator = (a, b) -> Long.compare(b.blockHeight, a.blockHeight);
        }
        filtered.sort(comparator);

        adapter.updateList(filtered);

        txtHistoryCount.setText(getString(R.string.history_tx_count, filtered.size()));

        TextView txtEmpty = findViewById(R.id.txtEmpty);
        RecyclerView recyclerView = findViewById(R.id.recyclerHistory);
        if (filtered.isEmpty()) {
            txtEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            txtEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @SuppressLint({"GestureBackNavigation", "MissingSuperCall"})
    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}