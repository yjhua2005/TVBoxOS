package com.github.tvbox.osc.drive.ui.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.drive.R;

import org.jetbrains.annotations.NotNull;

/**
 * 通用选择列表 Adapter。
 * [P2修复] 改用 ListAdapter 的 submitList() 发挥 DiffUtil 差异化更新能力，
 * 移除手动维护的 data 列表和 @SuppressLint 注解。
 */
public class SelectDialogAdapter<T> extends ListAdapter<T, SelectDialogAdapter.SelectViewHolder> {

    private boolean muteCheck = false;
    private int select = 0;
    private SelectDialogInterface dialogInterface = null;

    static class SelectViewHolder extends RecyclerView.ViewHolder {
        public SelectViewHolder(@NonNull @NotNull View itemView) {
            super(itemView);
        }
    }

    public interface SelectDialogInterface<T> {
        void click(T value, int pos);
        String getDisplay(T val);
    }

    public static DiffUtil.ItemCallback<String> stringDiff = new DiffUtil.ItemCallback<String>() {
        @Override
        public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
            return oldItem.equals(newItem);
        }
    };

    public SelectDialogAdapter(SelectDialogInterface dialogInterface, DiffUtil.ItemCallback diffCallback) {
        this(dialogInterface, diffCallback, false);
    }

    public SelectDialogAdapter(SelectDialogInterface dialogInterface, DiffUtil.ItemCallback diffCallback, boolean muteCheck) {
        super(diffCallback);
        this.dialogInterface = dialogInterface;
        this.muteCheck = muteCheck;
    }

    /**
     * [P2修复] 使用 submitList() 替代手动 data 操作，让 DiffUtil 正确计算差异并局部刷新。
     */
    public void setData(List<T> newData, int defaultSelect) {
        select = defaultSelect;
        submitList(newData != null ? new ArrayList<>(newData) : new ArrayList<>());
    }

    @SuppressLint("RecyclerView")
    @Override
    public SelectViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return new SelectViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.drive_item_dialog_select, parent, false));
    }

    @SuppressLint("RecyclerView")
    @Override
    public void onBindViewHolder(@NonNull @NotNull SelectViewHolder holder, int position) {
        T value = getItem(position);
        String name = dialogInterface.getDisplay(value);
        if (!muteCheck && position == select)
            name = "\u221A " + name;
        ((TextView) holder.itemView.findViewById(R.id.tvName)).setText(name);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!muteCheck && position == select) return;
                int oldSelect = select;
                select = position;
                notifyItemChanged(oldSelect);
                notifyItemChanged(select);
                dialogInterface.click(value, position);
            }
        });
    }
}