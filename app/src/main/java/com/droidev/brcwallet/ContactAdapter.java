package com.droidev.brcwallet;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ViewHolder> {

    public interface ContactActionListener {
        void onSend(Contact contact);

        void onEdit(Contact contact);

        void onDelete(Contact contact);
    }

    private List<Contact> contacts;
    private final ContactActionListener listener;

    public ContactAdapter(List<Contact> contacts, ContactActionListener listener) {
        this.contacts = contacts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Contact contact = contacts.get(position);
        holder.txtName.setText(contact.name);
        holder.txtAddress.setText(contact.address);

        holder.btnSend.setOnClickListener(v -> listener.onSend(contact));
        holder.btnEdit.setOnClickListener(v -> listener.onEdit(contact));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(contact));
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateList(List<Contact> newList) {
        this.contacts = newList;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtAddress;
        View btnSend, btnEdit, btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtContactName);
            txtAddress = itemView.findViewById(R.id.txtContactAddress);
            btnSend = itemView.findViewById(R.id.btnSendTo);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}