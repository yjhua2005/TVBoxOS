package com.github.tvbox.osc.drive.cache;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * [修复] 增加 SAF 授权 Uri 和可移动存储标识字段，
 * 为后续 SAF 持久化授权和 U 盘自动识别做准备。
 */
@Entity(tableName = "storageDrive")
public class StorageDrive {
    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "type")
    public int type;

    @ColumnInfo(name = "configJson")
    public String configJson;

    /** [新增] SAF 授权的持久化 Uri 字符串（如 content://...），用于 U 盘等可移动存储 */
    @ColumnInfo(name = "safUri")
    @Nullable
    public String safUri;

    /** [新增] 是否为可移动存储（U盘/TF卡） */
    @ColumnInfo(name = "isRemovable")
    public boolean isRemovable = false;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}