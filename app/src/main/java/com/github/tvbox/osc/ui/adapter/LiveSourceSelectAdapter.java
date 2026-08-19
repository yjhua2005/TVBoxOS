package com.github.tvbox.osc.ui.adapter;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 直播源分支列表适配器（用于 LiveSourceDialog 中的列表）
 * 每项显示名称 + 删除按钮
 */
public class LiveSourceSelectAdapter extends RecyclerView.Adapter<LiveSourceSelectAdapter.ViewHolder> {

    private ArrayList<String> data = new ArrayList<>();
    private int select = 0;
    private OnItemClickListener listener;
    private OnDeleteClickListener deleteListener;

    public interface OnItemClickListener {
        void onItemClick(String name, int position);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(int position);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        ImageView ivDelete;

        public ViewHolder(@NonNull @NotNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            ivDelete = itemView.findViewById(R.id.ivDelete);
        }
    }

    public LiveSourceSelectAdapter(OnItemClickListener listener, OnDeleteClickListener deleteListener) {
        this.listener = listener;
        this.deleteListener = deleteListener;
    }

    public void setData(List<String> newData, int defaultSelect) {
        data.clear();
        data.addAll(newData);
        select = defaultSelect;
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < data.size()) {
            data.remove(position);
            if (select >= data.size()) select = data.size() - 1;
            if (select < 0) select = 0;
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @NonNull
    @NotNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_live_source_select, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {
        String name = data.get(position);
        holder.tvName.setText(name);
        if (position == select) {
            holder.tvName.setTextColor(0xff02f8e1);
            holder.tvName.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        } else {
            holder.tvName.setTextColor(0xFFFFFFFF);
            holder.tvName.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
        }
        // 点击名称按钮 -> 选择（名称按钮独立聚焦）
        holder.tvName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (position == select) return;
                int oldSelect = select;
                select = position;
                notifyItemChanged(oldSelect);
                notifyItemChanged(select);
                if (listener != null) {
                    listener.onItemClick(name, position);
                }
            }
        });
        // 点击删除按钮
        holder.ivDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (deleteListener != null) {
                    deleteListener.onDeleteClick(position);
                }
            }
        });
    }
}
