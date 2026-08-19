package com.github.tvbox.osc.drive.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.drive.R;
import com.github.tvbox.osc.drive.bean.DriveFolderFile;
import com.github.tvbox.osc.drive.ui.dialog.AlistDriveDialog;
import com.github.tvbox.osc.drive.ui.dialog.FtpDriveDialog;
import com.github.tvbox.osc.drive.ui.dialog.SmbDriveDialog;
import com.github.tvbox.osc.drive.ui.dialog.WebdavDialog;
import com.github.tvbox.osc.drive.util.StorageDriveType;
import com.github.tvbox.osc.drive.widget.DriveTvRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 存储空间文件列表 Adapter。
 * <p>
 * 原来继承 BRVAH 的 BaseQuickAdapter，现改为标准 RecyclerView.Adapter。
 */
public class DriveAdapter extends RecyclerView.Adapter<DriveAdapter.ViewHolder> {

    private final List<DriveFolderFile> mData = new ArrayList<>();
    private RecyclerView mRecyclerView;

    public DriveAdapter() {
    }

    // ==================== 数据操作（兼容原 BRVAH 的 API 名称） ====================

    public void setNewData(List<DriveFolderFile> data) {
        mData.clear();
        if (data != null) mData.addAll(data);
        notifyDataSetChanged();
    }

    public DriveFolderFile getItem(int position) {
        if (position >= 0 && position < mData.size()) return mData.get(position);
        return null;
    }

    public List<DriveFolderFile> getData() {
        return mData;
    }

    public int getViewByPosition(int position, int viewId) {
        // 原 BRVAH 的 getViewByPosition 返回 int resId 语义不同，
        // 这里保留签名但实际不需要返回值（原代码中用于获取 View）
        return 0;
    }

    /**
     * 兼容原 BRVAH 的 getViewByPosition(View) 语义。
     * 返回指定位置的 item 中的某个 View。
     */
    public View findItemView(int position, int viewId) {
        RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForAdapterPosition(position);
        if (holder != null && holder.itemView != null) {
            return holder.itemView.findViewById(viewId);
        }
        return null;
    }

    // ==================== RecyclerView.Adapter ====================

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.mRecyclerView = recyclerView;
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.drive_item_drive, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DriveFolderFile item = mData.get(position);

        holder.itemName.setText((item.name == null && item.parentFolder == item) ? " . . " : item.name);
        holder.txtMediaName.setVisibility(View.GONE);
        holder.lastModified.setVisibility(View.GONE);
        holder.imgConfig.setVisibility(View.GONE);
        holder.delDrive.setVisibility(item.isDelMode ? View.VISIBLE : View.GONE);

        // WebDAV/Alist/FTP 删除模式下隐藏配置按钮（仅对 drive 顶层项有效）
        if (item.isDrive() && (item.getDriveType() == StorageDriveType.TYPE.WEBDAV
                || item.getDriveType() == StorageDriveType.TYPE.ALISTWEB
                || item.getDriveType() == StorageDriveType.TYPE.FTP
                || item.getDriveType() == StorageDriveType.TYPE.SMB)) {
            holder.imgConfig.setVisibility(item.isDelMode ? View.GONE : View.VISIBLE);
        }

        // 焦点变化 → 通知 DriveTvRecyclerView
        holder.mItemLayout.setOnFocusChangeListener((view, hasFocus) -> {
            holder.txtMediaName.setSelected(hasFocus);
            if (mRecyclerView instanceof DriveTvRecyclerView) {
                ((DriveTvRecyclerView) mRecyclerView).onFocusChange(holder.itemView, hasFocus);
            }
        });

        // 点击 → 通知 DriveTvRecyclerView
        holder.mItemLayout.setOnClickListener(view -> {
            if (mRecyclerView instanceof DriveTvRecyclerView) {
                ((DriveTvRecyclerView) mRecyclerView).onClick(holder.itemView);
            }
        });

        // 图标和类型处理
        if (item.isDrive()) {
            if (item.getDriveType() == StorageDriveType.TYPE.LOCAL) {
                holder.imgItem.setImageResource(R.drawable.drive_icon_sdcard);
            } else if (item.getDriveType() == StorageDriveType.TYPE.WEBDAV) {
                holder.imgItem.setImageResource(R.drawable.drive_icon_circle_node);
                holder.imgConfig.setOnClickListener(v -> {
                    WebdavDialog dialog = new WebdavDialog(v.getContext(), item.getDriveData());
                    dialog.show();
                });
            } else if (item.getDriveType() == StorageDriveType.TYPE.ALISTWEB) {
                holder.imgItem.setImageResource(R.drawable.drive_icon_alist);
                holder.imgConfig.setOnClickListener(v -> {
                    AlistDriveDialog dialog = new AlistDriveDialog(v.getContext(), item.getDriveData());
                    dialog.show();
                });
            } else if (item.getDriveType() == StorageDriveType.TYPE.FTP) {
                holder.imgItem.setImageResource(R.drawable.drive_icon_ftp);
                holder.imgConfig.setOnClickListener(v -> {
                    FtpDriveDialog dialog = new FtpDriveDialog(v.getContext(), item.getDriveData());
                    dialog.show();
                });
            } else if (item.getDriveType() == StorageDriveType.TYPE.SMB) {
                holder.imgItem.setImageResource(R.drawable.drive_icon_smb);
                holder.imgConfig.setOnClickListener(v -> {
                    SmbDriveDialog dialog = new SmbDriveDialog(v.getContext(), item.getDriveData());
                    dialog.show();
                });
            }
        } else {
            holder.lastModified.setText(item.getFormattedLastModified());
            holder.lastModified.setVisibility(View.VISIBLE);
            if (item.isFile) {
                if (item.fileType != null) {
                    holder.txtMediaName.setText(item.fileType);
                    holder.txtMediaName.setVisibility(View.VISIBLE);
                }
                if (StorageDriveType.isVideoType(item.fileType))
                    holder.imgItem.setImageResource(R.drawable.drive_icon_film);
                else
                    holder.imgItem.setImageResource(R.drawable.drive_icon_file);
            } else {
                holder.imgItem.setImageResource(R.drawable.drive_icon_folder);
            }
        }
    }

    // ==================== 删除模式 ====================

    public void toggleDelMode(boolean isDelMode) {
        for (int pos = 0; pos < mData.size(); pos++) {
            DriveFolderFile item = mData.get(pos);
            item.isDelMode = isDelMode;
            // WebDAV/Alist/FTP 删除模式下隐藏配置按钮（仅对 drive 顶层项有效）
            if (item.isDrive() && (item.getDriveType() == StorageDriveType.TYPE.WEBDAV
                    || item.getDriveType() == StorageDriveType.TYPE.ALISTWEB
                    || item.getDriveType() == StorageDriveType.TYPE.FTP
                    || item.getDriveType() == StorageDriveType.TYPE.SMB)) {
                View imgConfigView = findItemView(pos, R.id.imgConfig);
                if (imgConfigView != null) {
                    imgConfigView.setVisibility(isDelMode ? View.GONE : View.VISIBLE);
                }
            }
            View delView = findItemView(pos, R.id.delDrive);
            if (delView != null) {
                delView.setVisibility(isDelMode ? View.VISIBLE : View.GONE);
            }
        }
        notifyDataSetChanged();
    }

    // ==================== ViewHolder ====================

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView itemName;
        ImageView imgItem;
        TextView txtMediaName;
        TextView lastModified;
        ImageView imgConfig;
        LinearLayout mItemLayout;
        View delDrive;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            itemName = itemView.findViewById(R.id.txtItemName);
            imgItem = itemView.findViewById(R.id.imgItem);
            txtMediaName = itemView.findViewById(R.id.txtMediaName);
            lastModified = itemView.findViewById(R.id.txtModifiedDate);
            imgConfig = itemView.findViewById(R.id.imgConfig);
            mItemLayout = itemView.findViewById(R.id.mItemLayout);
            delDrive = itemView.findViewById(R.id.delDrive);
        }
    }
}