package com.github.tvbox.osc.drive.viewmodel;

import android.os.Handler;
import android.os.Looper;

import com.github.tvbox.osc.drive.bean.DriveFolderFile;
import com.google.gson.JsonObject;

import com.thegrizzlylabs.sardineandroid.DavResource;
import com.thegrizzlylabs.sardineandroid.Sardine;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * WebDAV 存储 ViewModel。
 * 依赖 sardine-android 库。
 *
 * [P0修复]
 * 1. targetPath != "" 改为 !targetPath.isEmpty()（修复字符串引用比较 bug）
 * 2. 异步线程中的回调通过 mainHandler.post() 切回主线程
 * 3. 补充 cancel() 覆写
 */
public class WebDAVDriveViewModel extends AbstractDriveViewModel {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Sardine webDAV;

    private boolean initWebDav() {
        if (webDAV != null) return true;
        try {
            JsonObject config = currentDrive.getConfig();
            webDAV = new OkHttpSardine();
            if (config.has("username") && config.has("password")) {
                webDAV.setCredentials(config.get("username").getAsString(), config.get("password").getAsString());
            }
            return true;
        } catch (Exception ex) {
        }
        return false;
    }

    private Sardine getWebDAV() {
        return initWebDav() ? webDAV : null;
    }

    @Override
    public String loadData(LoadDataCallback callback) {
        JsonObject config = currentDrive.getConfig();
        if (currentDriveNote == null) {
            currentDriveNote = new DriveFolderFile(null,
                    config.has("initPath") ? config.get("initPath").getAsString() : "", 0, false, null, null);
        }
        String targetPath = currentDriveNote.getAccessingPathStr() + currentDriveNote.name;
        if (currentDriveNote.getChildren() == null) {
            new Thread() {
                public void run() {
                    Sardine webDAV = getWebDAV();
                    if (webDAV == null) {
                        if (callback != null) {
                            mainHandler.post(() -> callback.fail("无法访问该WebDAV地址"));
                        }
                        return;
                    }
                    List<DavResource> files = null;
                    try {
                        files = webDAV.list(config.get("url").getAsString() + targetPath);
                    } catch (Exception ex) {
                        if (callback != null) {
                            mainHandler.post(() -> callback.fail("无法访问该WebDAV地址"));
                        }
                        return;
                    }

                    List<DriveFolderFile> items = new ArrayList<>();
                    if (files != null) {
                        for (DavResource file : files) {
                            // [P0修复] 原代码 targetPath != "" 是引用比较，永远为 true（除字符串池常量外）
                            // 改为 !targetPath.isEmpty() 进行值比较
                            if (!targetPath.isEmpty() && file.getPath().toUpperCase(Locale.ROOT).endsWith(targetPath.toUpperCase(Locale.ROOT) + "/"))
                                continue;
                            int extNameStartIndex = file.getName().lastIndexOf(".");
                            items.add(new DriveFolderFile(currentDriveNote, file.getName(), 0, !file.isDirectory(),
                                    !file.isDirectory() && extNameStartIndex >= 0 && extNameStartIndex < file.getName().length() ?
                                            file.getName().substring(extNameStartIndex + 1) : null,
                                    file.getModified().getTime()));
                        }
                    }
                    sortData(items);
                    DriveFolderFile backItem = new DriveFolderFile(null, null, 0, false, null, null);
                    backItem.parentFolder = backItem;
                    items.add(0, backItem);
                    currentDriveNote.setChildren(items);
                    // [P0修复] 回调必须通过 mainHandler 切回主线程，避免在非 UI 线程操作 View
                    if (callback != null) {
                        List<DriveFolderFile> result = currentDriveNote.getChildren();
                        mainHandler.post(() -> callback.callback(result, false));
                    }
                }
            }.start();
            return targetPath;
        } else {
            sortData(currentDriveNote.getChildren());
            if (callback != null)
                callback.callback(currentDriveNote.getChildren(), true);
        }
        return targetPath;
    }

    @Override
    public void cancel() {
        // WebDAV (sardine) 没有原生取消机制，下次请求会使用新的连接
    }
}