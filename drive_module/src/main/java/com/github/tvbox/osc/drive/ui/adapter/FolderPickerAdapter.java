package com.github.tvbox.osc.drive.ui.adapter;

import android.os.Environment;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.drive.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 目录选择器适配器。
 * [修复] 支持三种显示模式：
 * 1. 首页模式 — 外置存储在上（带 ../ 前缀），内部存储子目录在下
 * 2. 普通目录浏览模式 — 列表第一项为"返回上级"，后面为当前目录的子目录
 */
public class FolderPickerAdapter extends RecyclerView.Adapter<FolderPickerAdapter.ViewHolder> {

    public interface OnFolderClickListener {
        void onFolderClick(File folder);
        void onUpClick();
    }

    private static final Pattern USB_NAME = Pattern.compile("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}");

    /** 所有数据条目，类型由 itemTypes 列表对应 */
    private final List<File> items = new ArrayList<>();
    private final List<Integer> itemTypes = new ArrayList<>();
    private OnFolderClickListener listener;

    /** 是否处于首页模式 */
    private boolean homeMode = false;

    public FolderPickerAdapter(OnFolderClickListener listener) {
        this.listener = listener;
    }

    /**
     * 设置首页数据。
     * @param removableVolumes 外置存储列表（U盘/TF卡），显示在顶部带 ../ 前缀
     * @param internalSubDirs  内部存储子目录，显示在下方
     */
    public void setDataForHomePage(List<File> removableVolumes, List<File> internalSubDirs) {
        items.clear();
        itemTypes.clear();
        homeMode = true;

        // 外置存储放在最上面
        if (removableVolumes != null) {
            for (File vol : removableVolumes) {
                items.add(vol);
                itemTypes.add(TYPE_VOLUME);
            }
        }
        // 内部存储子目录放在下面
        if (internalSubDirs != null) {
            for (File dir : internalSubDirs) {
                items.add(dir);
                itemTypes.add(TYPE_FOLDER);
            }
        }
        notifyDataSetChanged();
    }

    /**
     * 设置普通目录浏览数据。
     */
    public void setData(File currentDir, List<File> subDirs) {
        items.clear();
        itemTypes.clear();
        homeMode = false;

        // 第一项：返回上级
        items.add(null);
        itemTypes.add(TYPE_UP);

        if (subDirs != null) {
            for (File dir : subDirs) {
                items.add(dir);
                itemTypes.add(TYPE_FOLDER);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static final int TYPE_UP = 0;
    private static final int TYPE_FOLDER = 1;
    private static final int TYPE_VOLUME = 2;

    @Override
    public int getItemViewType(int position) {
        return itemTypes.get(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.drive_item_folder_picker, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int type = itemTypes.get(position);

        if (type == TYPE_UP) {
            holder.tvName.setText("../ " + holder.itemView.getContext().getString(R.string.drive_picker_go_up));
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onUpClick();
            });
        } else if (type == TYPE_VOLUME) {
            File vol = items.get(position);
            String name = vol.getName();
            String label;
            if (USB_NAME.matcher(name).matches()) {
                label = "../" + name + "  (" + holder.itemView.getContext().getString(R.string.drive_picker_external) + ")";
            } else {
                label = "../" + name + "  (" + holder.itemView.getContext().getString(R.string.drive_picker_external) + ")";
            }
            holder.tvName.setText(label);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onFolderClick(vol);
            });
        } else {
            File folder = items.get(position);
            holder.tvName.setText(folder.getName());
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onFolderClick(folder);
            });
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvFolderName);
        }
    }
}