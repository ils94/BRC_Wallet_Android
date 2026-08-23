package com.droidev.brcwallet;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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
        TextView txtEmpty = findViewById(R.id.txtEmpty);
        Spinner spinnerSort = findViewById(R.id.spinnerSort);
        Spinner spinnerFilter = findViewById(R.id.spinnerFilter);

        store = new WalletStore(this);
        allTransactions = store.loadHistory();

        adapter = new TransactionAdapter(new ArrayList<>());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

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

        applyFiltersAndSort();

        uiHandler = new Handler(Looper.getMainLooper());
        updater = new Runnable() {
            @Override
            public void run() {
                updateBlockSubtitle();

                allTransactions = store.loadHistory();
                applyFiltersAndSort();

                uiHandler.postDelayed(this, 1000);
            }
        };
        uiHandler.post(updater);
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
            boolean include = false;
            switch (filterPos) {
                case 0:
                    include = true;
                    break;
                case 1:
                    include = (tx.type == TxRecord.Type.SEND);
                    break;
                case 2:
                    include = (tx.type == TxRecord.Type.RECEIVE);
                    break;
            }
            if (include) filtered.add(tx);
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
}