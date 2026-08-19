package com.github.tvbox.osc.drive.ui.dialog;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.github.tvbox.osc.drive.R;
import com.github.tvbox.osc.drive.ui.adapter.FolderPickerAdapter;
import com.github.tvbox.osc.drive.widget.DriveTvRecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 本地目录浏览器对话框。
 * [修复] 初始页面同时展示外置存储（U盘/TF卡）和内部存储子目录，
 * 外置存储排在上方，点击直接进入浏览。
 */
public class FolderPickerDialog extends DriveBaseDialog {

    public interface OnFolderSelectedListener {
        void onFolderSelected(String path);
    }

    private static final Pattern USB_PATH = Pattern.compile("/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}");

    private File currentDir;
    private final FolderPickerAdapter adapter;
    private final DriveTvRecyclerView recyclerView;
    private final TextView tvCurrentPath;
    private final OnFolderSelectedListener listener;

    public FolderPickerDialog(Context context, OnFolderSelectedListener listener) {
        super(context);
        setContentView(R.layout.drive_dialog_folder_picker);
        this.listener = listener;

        tvCurrentPath = findViewById(R.id.tvCurrentPath);
        recyclerView = findViewById(R.id.list);
        View btnConfirm = findViewById(R.id.btnConfirm);
        View btnCancel = findViewById(R.id.btnCancel);

        adapter = new FolderPickerAdapter(new FolderPickerAdapter.OnFolderClickListener() {
            @Override
            public void onFolderClick(File folder) {
                navigateTo(folder);
            }

            @Override
            public void onUpClick() {
                if (currentDir == null) {
                    Toast.makeText(getContext(), getContext().getString(R.string.drive_picker_already_root), Toast.LENGTH_SHORT).show();
                    return;
                }
                File parent = currentDir.getParentFile();
                if (parent != null && parent.isDirectory()) {
                    // 如果回到了 /storage 层级，重新展示合并首页
                    if (parent.getAbsolutePath().equals("/storage") || parent.getAbsolutePath().equals("/")) {
                        showHomePage();
                        return;
                    }
                    navigateTo(parent);
                } else {
                    showHomePage();
                }
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(adapter);

        btnConfirm.setOnClickListener(v -> {
            if (listener != null) {
                String selectedPath;
                if (currentDir != null) {
                    selectedPath = currentDir.getAbsolutePath();
                } else {
                    // 首页模式：未进入任何子目录，选定内部存储根目录
                    selectedPath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
                }
                listener.onFolderSelected(selectedPath);
            }
            dismiss();
        });

        btnCancel.setOnClickListener(v -> dismiss());

        // 初始显示合并首页
        showHomePage();
    }

    /**
     * 扫描 /storage 目录，分离外置存储和内部存储。
     * [修复] MuMu 模拟器等标准 Android 上 /storage/listFiles() 可能因权限返回 null，
     * 此时直接用 Environment.getExternalStorageDirectory() 兜底。
     */
    private void scanStorage(List<File> removableList, List<File> internalList) {
        File storageDir = new File("/storage");
        if (storageDir.exists() && storageDir.isDirectory()) {
            File[] children = storageDir.listFiles();
            if (children != null) {
                for (File f : children) {
                    if (!f.isDirectory() || f.isHidden()) continue;
                    String path = f.getAbsolutePath();
                    if (path.contains("emulated") || path.contains("self")) {
                        // 内部存储 — 跳过 emulated 根，直接用 /storage/emulated/0
                        File internalRoot = Environment.getExternalStorageDirectory();
                        if (internalRoot != null && internalRoot.exists()) {
                            internalList.add(internalRoot);
                        }
                    } else {
                        // 外置存储（U 盘 / TF 卡）
                        removableList.add(f);
                    }
                }
            }
        }

        // [修复] 兜底：/storage 扫描失败或为空时，直接用标准 API 获取内部存储
        if (removableList.isEmpty() && internalList.isEmpty()) {
            File internalRoot = Environment.getExternalStorageDirectory();
            if (internalRoot != null && internalRoot.exists() && internalRoot.isDirectory()) {
                internalList.add(internalRoot);
            }
        }
    }

    /**
     * 显示合并首页：
     * 顶部 = 外置存储（../storage/XXXX 外置存储）
     * 下方 = 内部存储的子目录
     */
    private void showHomePage() {
        currentDir = null;
        tvCurrentPath.setText(getContext().getString(R.string.drive_picker_title));

        List<File> removableVolumes = new ArrayList<>();
        List<File> internalList = new ArrayList<>();
        scanStorage(removableVolumes, internalList);

        // 获取内部存储的子目录
        List<File> internalSubDirs = new ArrayList<>();
        if (!internalList.isEmpty()) {
            File internalRoot = internalList.get(0);
            File[] children = internalRoot.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isDirectory() && !child.isHidden()) {
                        internalSubDirs.add(child);
                    }
                }
                Collections.sort(internalSubDirs, (o1, o2) ->
                        o1.getName().compareToIgnoreCase(o2.getName()));
            }
        }

        // [修复] MuMu 等标准 Android：权限不足时列表为空，提示用户授权
        if (removableVolumes.isEmpty() && internalSubDirs.isEmpty()) {
            Toast.makeText(getContext(),
                    getContext().getString(R.string.drive_picker_no_dirs), Toast.LENGTH_LONG).show();
        }

        adapter.setDataForHomePage(removableVolumes, internalSubDirs);
        recyclerView.post(() -> recyclerView.setSelection(0));
    }

    /**
     * 导航到指定目录。
     * [修复] 对 U 盘等可移动存储，跳过 canRead() 检查，直接尝试 listFiles()。
     */
    private void navigateTo(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            Toast.makeText(getContext(), getContext().getString(R.string.drive_picker_cannot_access, dir != null ? dir.getAbsolutePath() : "null"), Toast.LENGTH_SHORT).show();
            return;
        }

        // [修复] 彻底移除 canRead() 检查，对所有存储统一直接尝试 listFiles()。
        // 原因：小米/车机等定制系统的 canRead() 返回值完全不可靠，
        // FolderPickerDialog 能浏览说明 listFiles() 本身是通的。
        File[] children = dir.listFiles();
        if (children == null) {
            // listFiles() 真的失败了
            boolean isUsb = USB_PATH.matcher(dir.getAbsolutePath()).find();
            if (isUsb) {
                Toast.makeText(getContext(), getContext().getString(R.string.drive_picker_usb_fail), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getContext(), getContext().getString(R.string.drive_picker_read_fail, dir.getAbsolutePath()), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        currentDir = dir;
        tvCurrentPath.setText(dir.getAbsolutePath());

        List<File> subDirs = new ArrayList<>();
        for (File child : children) {
            if (child.isDirectory() && !child.isHidden()) {
                subDirs.add(child);
            }
        }
        Collections.sort(subDirs, (o1, o2) ->
                o1.getName().compareToIgnoreCase(o2.getName()));

        adapter.setData(dir, subDirs);
        recyclerView.post(() -> recyclerView.setSelection(0));
    }
}