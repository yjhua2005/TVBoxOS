package com.github.tvbox.osc.drive.ui.dialog;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.drive.R;
import com.github.tvbox.osc.drive.ui.dialog.FolderPickerDialog;
import com.github.tvbox.osc.drive.util.ArchiveHelper;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 压缩包解压对话框。
 * 点击 zip/7z 文件后弹出，提供三种解压目标选项：
 * 1. 解压到同名目录（如 test.zip → test/）
 * 2. 解压到当前目录（压缩包所在目录）
 * 3. 选择目录解压（打开目录选择器）
 */
public class ArchiveExtractDialog extends DriveBaseDialog {

    private final File archiveFile;
    private final File currentDir;
    private final OnExtractResultListener listener;

    private LinearLayout progressPanel;
    private ProgressBar progressBar;
    private TextView tvProgress;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    public interface OnExtractResultListener {
        void onExtractComplete(String targetPath);
    }

    /**
     * @param context     上下文
     * @param archiveFile 压缩包文件
     * @param currentDir  压缩包所在目录
     * @param listener    解压完成回调（可为 null）
     */
    public ArchiveExtractDialog(Context context, File archiveFile, File currentDir,
                                OnExtractResultListener listener) {
        super(context);
        this.archiveFile = archiveFile;
        this.currentDir = currentDir;
        this.listener = listener;
        setContentView(R.layout.drive_dialog_extract);
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 标题显示文件名
        TextView tvFileName = findViewById(R.id.tvFileName);
        tvFileName.setText(archiveFile.getName());

        progressPanel = findViewById(R.id.progressPanel);
        progressBar = findViewById(R.id.progressBar);
        tvProgress = findViewById(R.id.tvProgress);

        View btnSameName = findViewById(R.id.btnExtractSameName);
        View btnCurrent = findViewById(R.id.btnExtractCurrent);
        View btnCustom = findViewById(R.id.btnExtractCustom);
        View btnCancel = findViewById(R.id.btnCancel);

        // 1. 解压到同名目录
        btnSameName.setOnClickListener(v -> {
            String baseName = archiveFile.getName();
            int dotIdx = baseName.lastIndexOf('.');
            if (dotIdx > 0) {
                baseName = baseName.substring(0, dotIdx);
            }
            File targetDir = new File(currentDir, baseName);
            startExtract(targetDir);
        });

        // 2. 解压到当前目录
        btnCurrent.setOnClickListener(v -> {
            startExtract(currentDir);
        });

        // 3. 选择目录解压
        btnCustom.setOnClickListener(v -> {
            dismiss();
            // 打开目录选择器，选中后直接解压
            new FolderPickerDialog(getContext(), path -> {
                if (path == null || path.isEmpty()) return;
                startExtractInContext(new File(path));
            }).show();
        });

        // 取消
        btnCancel.setOnClickListener(v -> {
            cancelled.set(true);
            dismiss();
        });

        setOnDismissListener(d -> cancelled.set(true));
    }

    /**
     * 在对话框内显示进度并解压。
     */
    private void startExtract(final File outputDir) {
        // 切换到进度显示
        findViewById(R.id.btnExtractSameName).setVisibility(View.GONE);
        findViewById(R.id.btnExtractCurrent).setVisibility(View.GONE);
        findViewById(R.id.btnExtractCustom).setVisibility(View.GONE);
        findViewById(R.id.btnCancel).setVisibility(View.GONE);
        progressPanel.setVisibility(View.VISIBLE);

        outputDir.mkdirs();
        cancelled.set(false);

        new Thread(() -> {
            try {
                ArchiveHelper.extractAll(archiveFile, outputDir, null,
                        new ArchiveHelper.ProgressCallback() {
                            @Override
                            public void onProgress(int percent) {
                                uiHandler.post(() -> {
                                    progressBar.setProgress(percent);
                                    tvProgress.setText(percent + "%");
                                });
                            }

                            @Override
                            public void onCurrentFile(String fileName) {
                                uiHandler.post(() -> tvProgress.setText(
                                        getContext().getString(R.string.drive_extract_progress, getShortName(fileName))));
                            }

                            @Override
                            public boolean isCancelled() {
                                return cancelled.get();
                            }
                        });
                uiHandler.post(() -> {
                    Toast.makeText(getContext(),
                            getContext().getString(R.string.drive_extract_complete, outputDir.getAbsolutePath()),
                            Toast.LENGTH_SHORT).show();
                    if (listener != null) {
                        listener.onExtractComplete(outputDir.getAbsolutePath());
                    }
                    dismiss();
                });
            } catch (final Exception e) {
                uiHandler.post(() -> {
                    if (!cancelled.get()) {
                        Toast.makeText(getContext(),
                                getContext().getString(R.string.drive_extract_error, e.getMessage()),
                                Toast.LENGTH_SHORT).show();
                    }
                    dismiss();
                });
            }
        }).start();
    }

    /**
     * 对话框已关闭后的解压（用于"选择目录"场景）。
     * 需要独立显示 Toast。
     */
    private void startExtractInContext(File outputDir) {
        outputDir.mkdirs();
        Toast.makeText(getContext(), getContext().getString(R.string.drive_start_extract), Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                ArchiveHelper.extractAll(archiveFile, outputDir, null, null);
                uiHandler.post(() -> {
                    Toast.makeText(getContext(),
                            getContext().getString(R.string.drive_extract_complete, outputDir.getAbsolutePath()),
                            Toast.LENGTH_SHORT).show();
                    if (listener != null) {
                        listener.onExtractComplete(outputDir.getAbsolutePath());
                    }
                });
            } catch (final Exception e) {
                uiHandler.post(() -> Toast.makeText(getContext(),
                        getContext().getString(R.string.drive_extract_error, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private static String getShortName(String path) {
        int s = path.lastIndexOf('/');
        return s >= 0 ? path.substring(s + 1) : path;
    }
}