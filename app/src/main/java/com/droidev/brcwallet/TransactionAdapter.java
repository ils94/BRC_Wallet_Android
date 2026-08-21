package com.droidev.brcwallet;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    private final List<TxRecord> transactions;

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

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        TxRecord tx = transactions.get(position);
        holder.txtType.setText(getTypeLabel(context, tx.type));
        holder.txtDetails.setText(getDetails(context, tx));
    }

    @Override
    public int getItemCount() {
        return transactions.size();
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

    private String getDetails(Context context, TxRecord tx) {
        String amount = TxBuilder.weiToBrc(tx.amountWei);
        String feeSuffix = tx.feeWei > 0
                ? context.getString(R.string.tx_fee_suffix, TxBuilder.weiToBrc(tx.feeWei))
                : "";
        String from = tx.from.isEmpty() ? "?" : tx.from;
        String to = tx.to.isEmpty() ? "?" : tx.to;
        return context.getString(R.string.tx_details_format,
                tx.blockHeight, from, to, amount, feeSuffix);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtType, txtDetails;

        ViewHolder(View itemView) {
            super(itemView);
            txtType = itemView.findViewById(R.id.txtTxType);
            txtDetails = itemView.findViewById(R.id.txtTxDetails);
        }
    }
}