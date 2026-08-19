package com.github.tvbox.osc.drive.ui.dialog;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.drive.R;
import com.github.tvbox.osc.drive.ftp.SimpleFtpServer;
import com.github.tvbox.osc.drive.ui.adapter.FolderPickerAdapter;

import org.jetbrains.annotations.NotNull;

/**
 * FTP 服务器配置对话框。
 * 可配置端口、用户名、密码、根目录，并启动/停止 FTP 服务器。
 * 运行时显示 FTP 访问地址供其他设备连接。
 */
public class FtpServerConfigDialog extends DriveBaseDialog {

    private EditText etPort;
    private EditText etUsername;
    private EditText etPassword;
    private EditText etRootPath;
    private TextView tvStatus;
    private TextView tvFtpUrl;
    private View statusDot;
    private TextView btnStartStop;
    private SharedPreferences sp;

    public FtpServerConfigDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.drive_dialog_ftp_server);
        sp = context.getSharedPreferences("drive_ftp_server", Context.MODE_PRIVATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        etPort = findViewById(R.id.etPort);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etRootPath = findViewById(R.id.etRootPath);
        tvStatus = findViewById(R.id.tvStatus);
        tvFtpUrl = findViewById(R.id.tvFtpUrl);
        statusDot = findViewById(R.id.statusDot);
        btnStartStop = findViewById(R.id.btnStartStop);

        etPort.setFocusableInTouchMode(true);
        etPort.requestFocus();

        // 加载保存的配置
        etPort.setText(sp.getString("port", "3721"));
        etUsername.setText(sp.getString("username", ""));
        etPassword.setText(sp.getString("password", ""));
        String savedRoot = sp.getString("rootPath", "");
        if (savedRoot.isEmpty()) {
            savedRoot = Environment.getExternalStorageDirectory().getAbsolutePath();
        }
        etRootPath.setText(savedRoot);

        updateStatus();

        // 浏览按钮 → 打开目录选择器
        findViewById(R.id.btnBrowse).setOnClickListener(v -> {
            FolderPickerDialog picker = new FolderPickerDialog(getContext(), path -> {
                if (path != null && !path.isEmpty()) {
                    etRootPath.setText(path);
                }
            });
            picker.show();
        });

        // 启动/停止按钮
        btnStartStop.setOnClickListener(v -> {
            SimpleFtpServer server = SimpleFtpServer.getInstance();
            if (server.isRunning()) {
                server.stop();
                saveConfig();
                updateStatus();
                Toast.makeText(getContext(), getContext().getString(R.string.drive_ftp_server_stopped_hint), Toast.LENGTH_SHORT).show();
            } else {
                String portStr = etPort.getText().toString().trim();
                if (portStr.isEmpty()) {
                    Toast.makeText(getContext(), getContext().getString(R.string.drive_ftp_server_please_port), Toast.LENGTH_SHORT).show();
                    return;
                }
                int port;
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(getContext(), getContext().getString(R.string.drive_ftp_server_port_invalid), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (port < 1 || port > 65535) {
                    Toast.makeText(getContext(), getContext().getString(R.string.drive_ftp_server_port_range), Toast.LENGTH_SHORT).show();
                    return;
                }
                String rootPath = etRootPath.getText().toString().trim();
                if (rootPath.isEmpty()) {
                    Toast.makeText(getContext(), getContext().getString(R.string.drive_ftp_server_please_root), Toast.LENGTH_SHORT).show();
                    return;
                }
                java.io.File root = new java.io.File(rootPath);
                if (!root.exists() || !root.isDirectory()) {
                    Toast.makeText(getContext(), getContext().getString(R.string.drive_ftp_server_root_not_exist, rootPath), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (server.start(port,
                        etUsername.getText().toString().trim(),
                        etPassword.getText().toString().trim(),
                        rootPath)) {
                    saveConfig();
                    updateStatus();
                    Toast.makeText(getContext(), getContext().getString(R.string.drive_ftp_server_started), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), getContext().getString(R.string.drive_ftp_server_start_fail), Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 关闭按钮
        findViewById(R.id.btnClose).setOnClickListener(v -> dismiss());
    }

    /**
     * 刷新界面状态（运行/停止）。
     */
    private void updateStatus() {
        SimpleFtpServer server = SimpleFtpServer.getInstance();
        if (server.isRunning()) {
            tvStatus.setText(getContext().getString(R.string.drive_btn_running));
            statusDot.setBackgroundResource(R.drawable.drive_shape_status_running);
            String url = server.getFtpUrl();
            tvFtpUrl.setText(url);
            tvFtpUrl.setVisibility(View.VISIBLE);
            btnStartStop.setText(getContext().getString(R.string.drive_btn_stop));
            // 运行时禁止修改配置
            setFieldsEnabled(false);
        } else {
            tvStatus.setText(getContext().getString(R.string.drive_btn_stopped));
            statusDot.setBackgroundResource(R.drawable.drive_shape_status_stopped);
            tvFtpUrl.setVisibility(View.GONE);
            btnStartStop.setText(getContext().getString(R.string.drive_btn_start));
            setFieldsEnabled(true);
        }
    }

    /**
     * 设置配置字段是否可编辑。
     */
    private void setFieldsEnabled(boolean enabled) {
        etPort.setEnabled(enabled);
        etPort.setFocusableInTouchMode(enabled);
        etUsername.setEnabled(enabled);
        etUsername.setFocusableInTouchMode(enabled);
        etPassword.setEnabled(enabled);
        etPassword.setFocusableInTouchMode(enabled);
        etRootPath.setEnabled(enabled);
        etRootPath.setFocusableInTouchMode(enabled);
        findViewById(R.id.btnBrowse).setEnabled(enabled);
        findViewById(R.id.btnBrowse).setFocusable(enabled);
    }

    private void saveConfig() {
        sp.edit()
                .putString("port", etPort.getText().toString().trim())
                .putString("username", etUsername.getText().toString().trim())
                .putString("password", etPassword.getText().toString().trim())
                .putString("rootPath", etRootPath.getText().toString().trim())
                .apply();
    }
}