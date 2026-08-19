package com.github.tvbox.osc.drive.data;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.github.tvbox.osc.drive.cache.StorageDrive;
import com.github.tvbox.osc.drive.cache.StorageDriveDao;
import com.github.tvbox.osc.drive.util.StorageDriveType;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * 独立的数据管理器，只负责存储盘的增删改查。
 * 宿主 App 不再需要通过 RoomDataManger 操作存储盘。
 *
 * [修复] v2: 数据库迁移 + 新增 U 盘检测工具方法。
 */
public class DriveDataManager {

    private static final String DB_NAME = "drive_module.db";
    private static final String TAG = "DriveDataManager";

    private static volatile DriveDataManager manager;
    // [P1修复] 添加 volatile，确保 DCL 中 dbInstance 赋值对所有线程可见
    private static volatile DriveDatabase dbInstance;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    // [P2修复] 正则提取为静态常量，避免每次调用 isRemovablePath() 都重新编译
    private static final Pattern REMOVABLE_PATH_PATTERN = Pattern.compile("/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}");

    private DriveDataManager() {
    }

    /**
     * 必须在使用前调用，通常在 Application.onCreate() 中
     */
    public static void init(@NonNull Context context) {
        if (manager == null) {
            synchronized (DriveDataManager.class) {
                if (manager == null) {
                    manager = new DriveDataManager();
                    dbInstance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    DriveDatabase.class,
                                    DB_NAME)
                            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                            .addMigrations(MIGRATION_1_2)
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                }

                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    super.onOpen(db);
                                }
                            })
                            // [P1修复] 移除 allowMainThreadQueries()，所有数据库操作
                            // 应通过 getIoExecutor() 在 IO 线程执行，避免阻塞主线程
                            .build();
                }
            }
        }
    }

    /** [新增] 数据库 v1 → v2 迁移：新增 safUri、isRemovable 列 */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE storageDrive ADD COLUMN safUri TEXT DEFAULT NULL");
            db.execSQL("ALTER TABLE storageDrive ADD COLUMN isRemovable INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static DriveDatabase get() {
        if (manager == null) {
            throw new RuntimeException("DriveDataManager is not initialized. Call DriveDataManager.init(context) first.");
        }
        return dbInstance;
    }

    // ==================== 便捷方法 ====================

    public static void insertDriveRecord(@NonNull String name,
                                         @NonNull StorageDriveType.TYPE type,
                                         JsonObject config) {
        StorageDrive drive = new StorageDrive();
        drive.name = name;
        drive.type = type.ordinal();
        drive.configJson = config == null ? null : config.toString();
        // [修复] 自动检测是否为可移动存储
        drive.isRemovable = isRemovablePath(name);
        // [P1修复] 数据库写操作放入 IO 线程
        IO.execute(() -> get().getStorageDriveDao().insert(drive));
    }

    /**
     * [新增] 插入本地目录记录，支持标记是否为可移动存储。
     */
    public static void insertLocalDriveRecord(@NonNull String path, boolean isRemovable) {
        StorageDrive drive = new StorageDrive();
        drive.name = path;
        drive.type = StorageDriveType.TYPE.LOCAL.ordinal();
        drive.configJson = null;
        drive.isRemovable = isRemovable;
        // [P1修复] 数据库写操作放入 IO 线程
        IO.execute(() -> get().getStorageDriveDao().insert(drive));
    }

    public static void updateDriveRecord(@NonNull StorageDrive drive) {
        // [P1修复] 数据库写操作放入 IO 线程
        IO.execute(() -> get().getStorageDriveDao().insert(drive));
    }

    /**
     * [P1修复] 异步获取所有存储盘，通过回调返回，避免在主线程查询数据库。
     * 保留同步版本供内部短时调用（如 isDriveAlreadyAdded）。
     */
    public static void getAllDrivesAsync(DrivesCallback callback) {
        IO.execute(() -> {
            List<StorageDrive> drives = get().getStorageDriveDao().getAll();
            if (callback != null) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> callback.onResult(drives));
            }
        });
    }

    /**
     * [P1修复] 同步获取所有存储盘（仅供内部检查使用，避免阻塞主线程）。
     */
    public static List<StorageDrive> getAllDrives() {
        return get().getStorageDriveDao().getAll();
    }

    /** [修复] 返回删除行数，便于上层判断是否真正删除成功 */
    public static void deleteDrive(int id, DeleteCallback callback) {
        // [P1修复] 数据库删除操作放入 IO 线程
        IO.execute(() -> {
            int rows = get().getStorageDriveDao().delete(id);
            if (callback != null) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> callback.onResult(rows));
            }
        });
    }

    /** 同步删除（仅保留签名兼容，调用方应迁移到异步版本） */
    public static int deleteDrive(int id) {
        return get().getStorageDriveDao().delete(id);
    }

    public static ExecutorService getIoExecutor() {
        return IO;
    }

    // ==================== [新增] U 盘 / 存储卷检测工具方法 ====================

    /**
     * 获取系统所有已挂载的存储卷路径。
     * @param context Application Context
     * @return 所有可访问的存储卷 File 列表
     */
    public static List<File> getAllStorageVolumes(Context context) {
        List<File> volumes = new ArrayList<>();
        // 直接扫描 /storage 目录，兼容所有 Android 版本（避免 getDirectory() API 30 限制）
        File storageDir = new File("/storage");
        if (storageDir.exists() && storageDir.isDirectory()) {
            File[] children = storageDir.listFiles();
            if (children != null) {
                for (File f : children) {
                    // [修复] 移除 canRead() 检查，仅保留 isDirectory + 非隐藏
                    if (f.isDirectory() && !f.isHidden()) {
                        volumes.add(f);
                    }
                }
            }
        }
        // 兜底
        if (volumes.isEmpty()) {
            File external = Environment.getExternalStorageDirectory();
            if (external != null && external.exists()) {
                volumes.add(external);
            }
        }
        return volumes;
    }

    /**
     * 获取所有可移动存储卷（U盘、TF卡）。
     * @param context Application Context
     * @return 可移动存储的路径列表
     */
    public static List<File> getRemovableStorageVolumes(Context context) {
        List<File> all = getAllStorageVolumes(context);
        List<File> removable = new ArrayList<>();
        File internalStorage = Environment.getExternalStorageDirectory();
        for (File vol : all) {
            // 排除内部存储
            if (vol.getAbsolutePath().contains("emulated")) continue;
            if (vol.getAbsolutePath().equals(internalStorage.getAbsolutePath())) continue;
            removable.add(vol);
        }
        return removable;
    }

    /**
     * 判断路径是否为可移动存储。
     */
    public static boolean isRemovablePath(String path) {
        if (path == null) return false;
        // [P2修复] 使用预编译的静态常量正则，避免每次调用重新编译
        return REMOVABLE_PATH_PATTERN.matcher(path).find();
    }

    /**
     * [新增] 检查指定路径是否已存在于存储盘列表中。
     */
    public static boolean isDriveAlreadyAdded(String path) {
        List<StorageDrive> all = getAllDrives();
        for (StorageDrive drive : all) {
            if (drive.type == StorageDriveType.TYPE.LOCAL.ordinal() && path.equals(drive.name)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 异步回调接口 ====================

    /** 异步获取存储盘列表的回调 */
    public interface DrivesCallback {
        void onResult(List<StorageDrive> drives);
    }

    /** 异步删除的回调 */
    public interface DeleteCallback {
        void onResult(int deletedRows);
    }

    /** 布尔结果回调 */
    public interface BooleanCallback {
        void onResult(boolean result);
    }

    /**
     * [P1修复] 异步检查路径是否已添加，避免在主线程查询数据库。
     */
    public static void isDriveAlreadyAddedAsync(String path, BooleanCallback callback) {
        IO.execute(() -> {
            boolean result = isDriveAlreadyAdded(path);
            if (callback != null) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> callback.onResult(result));
            }
        });
    }
}