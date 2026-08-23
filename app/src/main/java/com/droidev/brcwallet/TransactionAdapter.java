package com.droidev.brcwallet;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    private static final String BLOCK_URL = "https://tabscope.netlify.app/#/block/%1$d";
    private static final String TX_URL = "https://tabscope.netlify.app/#/tx/%1$s";
    private static final String ADDRESS_URL = "https://tabscope.netlify.app/#/account/%1$s";

    private List<TxRecord> transactions;

    public TransactionAdapter(List<TxRecord> transactions) {
        this.transactions = transactions;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(v);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        TxRecord tx = transactions.get(position);

        holder.txtType.setText(getTypeLabel(context, tx.type));
        holder.txtAmount.setText(TxBuilder.weiToBrc(tx.amountWei));

        String blockValue = String.valueOf(tx.blockHeight);
        holder.txtBlockValue.setText(blockValue);
        holder.txtBlockValue.setOnClickListener(v -> openUrl(context, String.format(BLOCK_URL, tx.blockHeight)));

        String txid = (tx.txid == null || tx.txid.isEmpty()) ? "?" : tx.txid;
        holder.txtTxidValue.setText(txid);
        if (!txid.equals("?")) {
            holder.txtTxidValue.setOnClickListener(v -> openUrl(context, String.format(TX_URL, txid)));
        } else {
            holder.txtTxidValue.setOnClickListener(null);
        }

        String from = tx.from.isEmpty() ? "?" : tx.from;
        holder.txtFromValue.setText(from);
        if (!from.equals("?")) {
            holder.txtFromValue.setOnClickListener(v -> openUrl(context, String.format(ADDRESS_URL, from)));
        } else {
            holder.txtFromValue.setOnClickListener(null);
        }

        String to = tx.to.isEmpty() ? "?" : tx.to;
        holder.txtToValue.setText(to);
        if (!to.equals("?")) {
            holder.txtToValue.setOnClickListener(v -> openUrl(context, String.format(ADDRESS_URL, to)));
        } else {
            holder.txtToValue.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateList(List<TxRecord> newList) {
        this.transactions = newList;
        notifyDataSetChanged();
    }

    private String getTypeLabel(Context context, TxRecord.Type type) {
        switch (type) {
            case SEND:
                return context.getString(R.string.tx_type_send);
            case RECEIVE:
                return context.getString(R.string.tx_type_receive);
            case MINE:
                return context.getString(R.string.tx_type_mine);
            case LOCK:
                return context.getString(R.string.tx_type_lock);
            case REDEEM:
                return context.getString(R.string.tx_type_redeem);
            default:
                return "?";
        }
    }

    private void openUrl(Context context, String url) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivity(browserIntent);
        } catch (Exception e) {
            Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show();
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtType, txtAmount;
        TextView txtBlockValue, txtTxidValue, txtFromValue, txtToValue;

        ViewHolder(View itemView) {
            super(itemView);
            txtType = itemView.findViewById(R.id.txtTxType);
            txtAmount = itemView.findViewById(R.id.txtTxAmount);
            txtBlockValue = itemView.findViewById(R.id.txtBlockValue);
            txtTxidValue = itemView.findViewById(R.id.txtTxidValue);
            txtFromValue = itemView.findViewById(R.id.txtFromValue);
            txtToValue = itemView.findViewById(R.id.txtToValue);
        }
    }
}