package com.github.tvbox.osc.drive.data;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.github.tvbox.osc.drive.cache.StorageDrive;
import com.github.tvbox.osc.drive.cache.StorageDriveDao;

/**
 * 独立的 Room 数据库，只包含存储盘相关的表。
 * 与宿主 App 的 AppDataBase 完全隔离。
 *
 * [修复] 版本 1 → 2：storageDrive 表新增 safUri、isRemovable 字段。
 */
@Database(entities = {StorageDrive.class}, version = 2, exportSchema = false)
public abstract class DriveDatabase extends RoomDatabase {
    public abstract StorageDriveDao getStorageDriveDao();
}