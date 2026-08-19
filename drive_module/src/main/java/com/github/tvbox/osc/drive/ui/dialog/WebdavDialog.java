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

public class WebdavDialog extends DriveBaseDialog {

    private StorageDrive drive = null;
    private EditText etName;
    private EditText etUrl;
    private EditText etInitPath;
    private EditText etUsername;
    private EditText etPassword;

    public WebdavDialog(@NonNull @NotNull Context context, StorageDrive drive) {
        super(context);
        setContentView(R.layout.drive_dialog_webdav);
        if (drive != null) this.drive = drive;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        etName = findViewById(R.id.etName);
        etUrl = findViewById(R.id.etUrl);
        etInitPath = findViewById(R.id.etInitPath);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etName.setFocusableInTouchMode(true);
        etName.requestFocus();
        if (drive != null) {
            etName.setText(drive.name);
            try {
                JsonObject config = JsonParser.parseString(drive.configJson).getAsJsonObject();
                initSavedData(etUrl, config, "url");
                initSavedData(etInitPath, config, "initPath");
                initSavedData(etUsername, config, "username");
                initSavedData(etPassword, config, "password");
            } catch (Exception ex) {
            }
        }
        findViewById(R.id.btnConfirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String name = etName.getText().toString();
                String url = etUrl.getText().toString();
                String initPath = etInitPath.getText().toString();
                String username = etUsername.getText().toString();
                String password = etPassword.getText().toString();
                // [P2修复] 统一使用 trim().isEmpty() 进行输入验证
                if (name.trim().isEmpty()) {
                    Toast.makeText(getContext(), getContext().getString(R.string.drive_webdav_please_name), Toast.LENGTH_SHORT).show();
                    return;
                }
                // [P2修复] 统一使用 trim().isEmpty() 进行输入验证
                if (url.trim().isEmpty()) {
                    Toast.makeText(getContext(), getContext().getString(R.string.drive_webdav_please_url), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!url.endsWith("/")) url += "/";
                JsonObject config = new JsonObject();
                config.addProperty("url", url);
                if (initPath.length() > 0 && initPath.startsWith("/"))
                    initPath = initPath.substring(1);
                if (initPath.length() > 0 && initPath.endsWith("/"))
                    initPath = initPath.substring(0, initPath.length() - 1);
                config.addProperty("initPath", initPath);
                config.addProperty("username", username);
                config.addProperty("password", password);
                if (drive != null) {
                    drive.name = name;
                    drive.configJson = config.toString();
                    DriveDataManager.updateDriveRecord(drive);
                } else {
                    DriveDataManager.insertDriveRecord(name, StorageDriveType.TYPE.WEBDAV, config);
                }
                EventBus.getDefault().post(new DriveEvent(DriveEvent.TYPE_DRIVE_REFRESH));
                WebdavDialog.this.dismiss();
            }
        });
        findViewById(R.id.btnCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WebdavDialog.this.dismiss();
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