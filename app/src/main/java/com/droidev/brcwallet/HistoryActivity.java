package com.droidev.brcwallet;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

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

        WalletStore store = new WalletStore(this);
        List<TxRecord> history = store.loadHistory();

        if (history.isEmpty()) {
            txtEmpty.setVisibility(TextView.VISIBLE);
            recyclerView.setVisibility(RecyclerView.GONE);
        } else {
            txtEmpty.setVisibility(TextView.GONE);
            recyclerView.setVisibility(RecyclerView.VISIBLE);

            history.sort((a, b) -> Long.compare(b.blockHeight, a.blockHeight));

            TransactionAdapter adapter = new TransactionAdapter(history);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(adapter);
        }
    }
}