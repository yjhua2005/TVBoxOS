package com.github.tvbox.osc.drive.viewmodel;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.github.tvbox.osc.drive.bean.DriveFolderFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地目录存储 ViewModel。
 *
 * [P0修复] 将文件列表加载改为异步执行，避免大目录阻塞主线程导致 ANR。
 * 原 doLoadData 在调用线程（主线程）同步执行 listFiles()，
 * 对于含大量文件的目录（如 /storage 根目录）会卡死 UI。
 */
public class LocalDriveViewModel extends AbstractDriveViewModel {

    private static final String TAG = "LocalDriveVM";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public String loadData(LoadDataCallback callback) {
        if (currentDriveNote == null)
            currentDriveNote = new DriveFolderFile(null, "", 0, false, null, null);

        String pathStr = currentDrive.name + currentDriveNote.getAccessingPathStr() + currentDriveNote.name;
        File targetDir = new File(pathStr);
        String path = targetDir.getAbsolutePath();

        Log.d(TAG, "loadData path=" + path);

        if (currentDriveNote.getChildren() == null) {
            // [P0修复] 异步加载本地文件列表，避免大目录阻塞主线程
            new Thread(() -> {
                try {
                    doLoadDataAsync(targetDir, path, callback);
                } catch (Throwable e) {
                    Log.e(TAG, "loadData error for " + path, e);
                    if (callback != null) {
                        String msg = "读取目录出错: " + e.getMessage();
                        mainHandler.post(() -> callback.fail(msg));
                    }
                }
            }).start();
        } else {
            sortData(currentDriveNote.getChildren());
            if (callback != null) {
                callback.callback(currentDriveNote.getChildren(), true);
            }
        }
        return path;
    }

    private void doLoadDataAsync(File targetDir, String path, LoadDataCallback callback) {
        // 仅保留最基础的存在性校验
        if (!targetDir.exists() || !targetDir.isDirectory()) {
            if (callback != null) {
                String msg = "目录不存在: " + path;
                mainHandler.post(() -> callback.fail(msg));
            }
            return;
        }

        File[] files = targetDir.listFiles();
        Log.d(TAG, "listFiles() for " + path + " -> " + (files != null ? files.length + " items" : "null"));

        if (files == null) {
            if (callback != null) {
                mainHandler.post(() -> callback.fail("无法读取目录内容，请确认存储权限已授予"));
            }
            return;
        }

        List<DriveFolderFile> items = new ArrayList<>();
        for (File file : files) {
            if (file.isHidden()) continue;
            int extNameStartIndex = file.getName().lastIndexOf(".");
            items.add(new DriveFolderFile(currentDriveNote, file.getName(), 0, file.isFile(),
                    file.isFile() && extNameStartIndex >= 0 && extNameStartIndex < file.getName().length() ?
                            file.getName().substring(extNameStartIndex + 1) : null,
                    file.lastModified()));
        }

        sortData(items);
        DriveFolderFile backItem = new DriveFolderFile(null, null, 0, false, null, null);
        backItem.parentFolder = backItem;
        items.add(0, backItem);
        currentDriveNote.setChildren(items);

        // [P0修复] 通过 mainHandler 回到主线程回调，与 DriveActivity.loadDriveData 中
        // mHandler.post() 保持一致的线程模型
        if (callback != null) {
            List<DriveFolderFile> result = currentDriveNote.getChildren();
            mainHandler.post(() -> callback.callback(result, false));
        }
    }
}