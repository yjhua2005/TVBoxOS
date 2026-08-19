package com.github.tvbox.osc.drive.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.drive.R;
import com.github.tvbox.osc.drive.cache.StorageDrive;
import com.github.tvbox.osc.drive.data.DriveDataManager;
import com.github.tvbox.osc.drive.event.DriveEvent;
import com.github.tvbox.osc.drive.event.DriveInputMsgEvent;
import com.github.tvbox.osc.drive.util.StorageDriveType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

public class FtpDriveDialog extends DriveBaseDialog {

    private StorageDrive drive = null;
    private EditText etName;
    private EditText etHost;
    private EditText etPort;
    private EditText etInitPath;
    private EditText etUsername;
    private EditText etPassword;
    private EditText etEncoding;

    public FtpDriveDialog(@NonNull @NotNull Context context, StorageDrive drive) {
        super(context);
        setContentView(R.layout.drive_dialog_ftp);
        if (drive != null) this.drive = drive;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        etName = findViewById(R.id.etName);
        etHost = findViewById(R.id.etHost);
        etPort = findViewById(R.id.etPort);
        etInitPath = findViewById(R.id.etInitPath);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etEncoding = findViewById(R.id.etEncoding);
        etName.setFocusableInTouchMode(true);
        etName.requestFocus();

        if (drive != null) {
            etName.setText(drive.name);
            try {
                JsonObject config = JsonParser.parseString(drive.configJson).getAsJsonObject();
                initSavedData(etHost, config, "host");
                initSavedData(etPort, config, "port");
                initSavedData(etInitPath, config, "initPath");
                initSavedData(etUsername, config, "username");
                initSavedData(etPassword, config, "password");
                initSavedData(etEncoding, config, "encoding");
            } catch (Exception ex) {
            }
        }

        findViewById(R.id.btnConfirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String name = etName.getText().toString().trim();
                String host = etHost.getText().toString().trim();
                String portStr = etPort.getText().toString().trim();
                String initPath = etInitPath.getText().toString().trim();
                String username = etUsername.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                String encoding = etEncoding.getText().toString().trim();

                if (name.isEmpty()) {
                    Toast.makeText(getContext(), getContext().getString(R.string.drive_ftp_please_name), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (host.isEmpty()) {
                    Toast.makeText(getContext(), getContext().getString(R.string.drive_ftp_please_addr), Toast.LENGTH_SHORT).show();
                    return;
                }

                int port = 21;
                if (!portStr.isEmpty()) {
                    try {
                        port = Integer.parseInt(portStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(getContext(), getContext().getString(R.string.drive_ftp_port_invalid), Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                // 规范化 initPath
                if (initPath.isEmpty()) initPath = "/";
                if (!initPath.startsWith("/")) initPath = "/" + initPath;
                if (initPath.length() > 1 && initPath.endsWith("/"))
                    initPath = initPath.substring(0, initPath.length() - 1);

                if (encoding.isEmpty()) encoding = "UTF-8";

                JsonObject config = new JsonObject();
                config.addProperty("host", host);
                config.addProperty("port", port);
                config.addProperty("initPath", initPath);
                config.addProperty("username", username);
                config.addProperty("password", password);
                config.addProperty("encoding", encoding);

                if (drive != null) {
                    drive.name = name;
                    drive.configJson = config.toString();
                    DriveDataManager.updateDriveRecord(drive);
                } else {
                    DriveDataManager.insertDriveRecord(name, StorageDriveType.TYPE.FTP, config);
                }
                EventBus.getDefault().post(new DriveEvent(DriveEvent.TYPE_DRIVE_REFRESH));
                FtpDriveDialog.this.dismiss();
            }
        });

        findViewById(R.id.btnCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FtpDriveDialog.this.dismiss();
            }
        });
    }

    private void initSavedData(EditText etField, JsonObject config, String fieldName) {
        if (config.has(fieldName))
            etField.setText(config.get(fieldName).getAsString());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onInputMsgEvent(DriveInputMsgEvent inputMsgEvent) {
        View vFocus = this.getWindow().getDecorView().findFocus();
        if (vFocus instanceof EditText) {
            ((EditText) vFocus).setText(inputMsgEvent.getText());
        }
    }
}