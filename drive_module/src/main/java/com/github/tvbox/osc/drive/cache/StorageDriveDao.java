package com.github.tvbox.osc.drive.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface StorageDriveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(StorageDrive drive);

    @Query("select * from storageDrive order by id")
    List<StorageDrive> getAll();

    /** [修复] 返回删除行数，便于上层判断是否真正删除成功 */
    @Query("DELETE FROM storageDrive WHERE id = :id")
    int delete(int id);
}