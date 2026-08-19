package com.github.tvbox.osc.drive.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.github.tvbox.osc.drive.R;
import com.github.tvbox.osc.drive.cache.StorageDrive;
import com.github.tvbox.osc.drive.data.DriveDataManager;
import com.github.tvbox.osc.drive.event.DriveEvent;
import com.github.tvbox.osc.drive.event.DriveInputMsgEvent;
import com.github.tvbox.osc.drive.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.drive.util.SmbDiscoveryUtil;
import com.github.tvbox.osc.drive.util.StorageDriveType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * SMB 存储盘配置对话框。
 * 包含：服务器地址（含 mDNS 扫描）、端口、共享名称、域名、用户名、密码、SMB 版本选择。
 */
public class SmbDriveDialog extends DriveBaseDialog {

    private final Context hostContext;
    private StorageDrive drive = null;
    private int smbVersion = 0; // 0=自动 1=SMB1.0 2=SMB2.0 3=SMB3.0

    private EditText etName;
    private EditText etHost;
    private EditText etPort;
    private EditText etShareName;
    private EditText etDomain;
    private EditText etUsername;
    private EditText etPassword;
    private TextView tvSmbVersion;
    private TextView btnScan;

    /** 当前正在进行的 mDNS 发现，用于提前取消 */
    private NsdManager.DiscoveryListener currentDiscovery;
    private boolean scanning = false;

    private static final int SMB_VERSION_COUNT = 4;

    private List<String> getSmbVersionLabels() {
        return Arrays.asList(
            getContext().getString(R.string.drive_smb_version_auto),
            getContext().getString(R.string.drive_smb_version_1),
            getContext().getString(R.string.drive_smb_version_2),
            getContext().getString(R.string.drive_smb_version_3));
    }

    public SmbDriveDialog(@NonNull @NotNull Context context, StorageDrive drive) {
        super(context);
        this.hostContext = context;
        setContentView(R.layout.drive_dialog_smb);
        if (drive != null) this.drive = drive;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        etName = findViewById(R.id.etName);
        etHost = findViewById(R.id.etHost);
        etPort = findViewById(R.id.etPort);
        etShareName = findViewById(R.id.etShareName);
        etDomain = findViewById(R.id.etDomain);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        tvSmbVersion = findViewById(R.id.tvSmbVersion);
        btnScan = findViewById(R.id.btnScan);

        etName.setFocusableInTouchMode(true);
        etName.requestFocus();

        if (drive != null) {
            etName.setText(drive.name);
            try {
                JsonObject config = JsonParser.parseString(drive.configJson).getAsJsonObject();
                initField(etHost, config, "host");
                initField(etPort, config, "port");
                initField(etShareName, config, "shareName");
                initField(etDomain, config, "domain");
                initField(etUsername, config, "username");
                initField(etPassword, config, "password");
                if (config.has("smbVersion")) {
                    smbVersion = config.get("smbVersion").getAsInt();
                    // 兼容旧版本映射：旧 1=SMB2.0, 2=SMB3.0 → 新 2=SMB2.0, 3=SMB3.0
                    if (smbVersion == 1 || smbVersion == 2) {
                        smbVersion += 1;
                    }
                }
            } catch (Exception ex) {
            }
        }
        tvSmbVersion.setText(getSmbVersionLabels().get(smbVersion));

        // mDNS 扫描按钮
        btnScan.setOnClickListener(v -> startDiscovery());

        // SMB 版本选择器
        tvSmbVersion.setOnClickListener(v -> openVersionSelector());

        // 确定
        findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String host = etHost.getText().toString().trim();
            String portStr = etPort.getText().toString().trim();
            String shareName = etShareName.getText().toString().trim();
            String domain = etDomain.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(getContext(), getContext().getString(R.string.drive_smb_please_name), Toast.LENGTH_SHORT).show();
                return;
            }
            if (host.isEmpty()) {
                Toast.makeText(getContext(), getContext().getString(R.string.drive_smb_please_addr), Toast.LENGTH_SHORT).show();
                return;
            }
            if (shareName.isEmpty()) {
                Toast.makeText(getContext(), getContext().getString(R.string.drive_smb_please_share), Toast.LENGTH_SHORT).show();
                return;
            }

            int port = 445;
            if (!portStr.isEmpty()) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(getContext(), getContext().getString(R.string.drive_smb_port_invalid), Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            JsonObject config = new JsonObject();
            config.addProperty("host", host);
            config.addProperty("port", port);
            config.addProperty("shareName", shareName);
            config.addProperty("domain", domain);
            config.addProperty("username", username);
            config.addProperty("password", password);
            config.addProperty("smbVersion", smbVersion);

            if (drive != null) {
                drive.name = name;
                drive.configJson = config.toString();
                DriveDataManager.updateDriveRecord(drive);
            } else {
                DriveDataManager.insertDriveRecord(name, StorageDriveType.TYPE.SMB, config);
            }
            EventBus.getDefault().post(new DriveEvent(DriveEvent.TYPE_DRIVE_REFRESH));
            SmbDriveDialog.this.dismiss();
        });

        // 取消
        findViewById(R.id.btnCancel).setOnClickListener(v -> SmbDriveDialog.this.dismiss());
    }

    // ==================== mDNS 扫描 ====================

    private void startDiscovery() {
        if (scanning) return;
        scanning = true;
        btnScan.setText(getContext().getString(R.string.drive_btn_scanning));
        Toast.makeText(getContext(), getContext().getString(R.string.drive_smb_scan_hint), Toast.LENGTH_SHORT).show();

        // 取消上一次未完成的发现
        if (currentDiscovery != null) {
            try {
                NsdManager nsdManager = (NsdManager) getContext().getSystemService(Context.NSD_SERVICE);
                if (nsdManager != null) nsdManager.stopServiceDiscovery(currentDiscovery);
            } catch (Exception ignored) {}
        }

        currentDiscovery = SmbDiscoveryUtil.discover(getContext(), 5000, devices -> {
            scanning = false;
            btnScan.setText(getContext().getString(R.string.drive_btn_scan));
            if (devices.isEmpty()) {
                Toast.makeText(getContext(), getContext().getString(R.string.drive_smb_not_found), Toast.LENGTH_SHORT).show();
                return;
            }
            showDiscoveredDevices(devices);
        });
    }

    /**
     * 发现到设备后弹出选择列表，选中后自动填充服务器地址和端口。
     */
    private void showDiscoveredDevices(List<SmbDiscoveryUtil.DiscoveredDevice> devices) {
        if (!(hostContext instanceof Activity)) return;

        SelectDialog<SmbDiscoveryUtil.DiscoveredDevice> dialog = new SelectDialog<>((Activity) hostContext);
        dialog.setTip(getContext().getString(R.string.drive_smb_found, devices.size()));
        dialog.setItemCheckDisplay(false);
        dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<SmbDiscoveryUtil.DiscoveredDevice>() {
            @Override
            public void click(SmbDiscoveryUtil.DiscoveredDevice value, int pos) {
                etHost.setText(value.host);
                if (value.port != 445) {
                    etPort.setText(String.valueOf(value.port));
                } else {
                    etPort.setText("");
                }
                // 空间名称自动填充（如果为空）
                if (etName.getText().toString().trim().isEmpty()) {
                    etName.setText(value.name);
                }
                dialog.dismiss();
            }

            @Override
            public String getDisplay(SmbDiscoveryUtil.DiscoveredDevice val) {
                return val.name + "  (" + val.host + ":" + val.port + ")";
            }
        }, new DiffUtil.ItemCallback<SmbDiscoveryUtil.DiscoveredDevice>() {
            @Override
            public boolean areItemsTheSame(@NonNull SmbDiscoveryUtil.DiscoveredDevice o, @NonNull SmbDiscoveryUtil.DiscoveredDevice n) {
                return o.host.equals(n.host);
            }

            @Override
            public boolean areContentsTheSame(@NonNull SmbDiscoveryUtil.DiscoveredDevice o, @NonNull SmbDiscoveryUtil.DiscoveredDevice n) {
                return o.host.equals(n.host) && o.port == n.port;
            }
        }, devices, 0);
        dialog.show();
    }

    @Override
    public void dismiss() {
        // 关闭对话框时停止发现
        if (currentDiscovery != null) {
            try {
                NsdManager nsdManager = (NsdManager) getContext().getSystemService(Context.NSD_SERVICE);
                if (nsdManager != null) nsdManager.stopServiceDiscovery(currentDiscovery);
            } catch (Exception ignored) {}
            currentDiscovery = null;
        }
        super.dismiss();
    }

    // ==================== SMB 版本选择 ====================

    private void openVersionSelector() {
        if (!(hostContext instanceof Activity)) return;
        SelectDialog<String> dialog = new SelectDialog<>((Activity) hostContext);
        dialog.setTip(getContext().getString(R.string.drive_smb_choose_version));
        dialog.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override
            public void click(String value, int pos) {
                smbVersion = pos;
                tvSmbVersion.setText(getSmbVersionLabels().get(pos));
                dialog.dismiss();
            }

            @Override
            public String getDisplay(String val) {
                return val;
            }
        }, new DiffUtil.ItemCallback<String>() {
            @Override
            public boolean areItemsTheSame(@NonNull String o, @NonNull String n) {
                return o.equals(n);
            }

            @Override
            public boolean areContentsTheSame(@NonNull String o, @NonNull String n) {
                return o.equals(n);
            }
        }, getSmbVersionLabels(), smbVersion);
        dialog.show();
    }

    private void initField(EditText et, JsonObject config, String key) {
        if (config.has(key)) et.setText(config.get(key).getAsString());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onInputMsgEvent(DriveInputMsgEvent inputMsgEvent) {
        View vFocus = this.getWindow().getDecorView().findFocus();
        if (vFocus instanceof EditText) {
            ((EditText) vFocus).setText(inputMsgEvent.getText());
        }
    }
}