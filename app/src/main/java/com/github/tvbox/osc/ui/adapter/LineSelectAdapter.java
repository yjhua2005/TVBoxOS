package com.github.tvbox.osc.ui.adapter;


import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MoreSourceBean;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 线路选择列表适配器（参照ysc的jy0）
 * 用MoreSourceBean代替String，支持删除按钮显隐控制
 * 删除按钮规则（参照ysc c21第59行 + y11.OooO0Oo第177行）：
 *   - 仓库urls解析出的线路 → 不显示删除（showDelete=false）
 *   - api_history_list中用户手动添加的 → 显示删除（showDelete=true）
 */
public class LineSelectAdapter extends RecyclerView.Adapter<LineSelectAdapter.ViewHolder> {

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        ImageView ivDelete;
        View divider;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            ivDelete = itemView.findViewById(R.id.ivDelete);
            divider = itemView.findViewById(R.id.divider);
        }
    }

    public interface OnLineClickListener {
        void onLineClick(MoreSourceBean bean, int position);
        void onLineDelete(MoreSourceBean bean, int position);
    }

    private ArrayList<MoreSourceBean> data = new ArrayList<>();
    private int selectPos = 0;
    private OnLineClickListener listener;

    public LineSelectAdapter(OnLineClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<MoreSourceBean> newData, int defaultSelect) {
        data.clear();
        data.addAll(newData);
        selectPos = defaultSelect;
        notifyDataSetChanged();
    }

    public ArrayList<MoreSourceBean> getData() {
        return data;
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_line_select, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull ViewHolder holder, final int position) {
        MoreSourceBean bean = data.get(position);
        String name = bean.getSourceName();
        if (name == null || name.isEmpty()) {
            name = bean.getSourceUrl();
        }
        holder.tvName.setText(name);

        // 选中状态（参照ysc c21: 选中用强调色）
        if (position == selectPos) {
            holder.tvName.setTextColor(0xff02f8e1);
            holder.tvName.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        } else {
            holder.tvName.setTextColor(0xFFFFFFFF);
            holder.tvName.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
        }

        // 删除按钮显隐（参照ysc c21第59行: custom_store_house.contains || showDelete）
        // 线路切换场景：仓库自带的线路showDelete=false不显示，用户手动添加的showDelete=true显示
        boolean showDel = bean.getShowDelete();
        holder.ivDelete.setVisibility(showDel ? View.VISIBLE : View.GONE);
        holder.divider.setVisibility(showDel ? View.VISIBLE : View.GONE);

        // 点击线路名 → 选中
        holder.tvName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int oldPos = selectPos;
                selectPos = position;
                notifyItemChanged(oldPos);
                notifyItemChanged(selectPos);
                if (listener != null) {
                    listener.onLineClick(bean, position);
                }
            }
        });

        // 点击删除按钮
        holder.ivDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onLineDelete(bean, position);
                }
            }
        });
    }
}
