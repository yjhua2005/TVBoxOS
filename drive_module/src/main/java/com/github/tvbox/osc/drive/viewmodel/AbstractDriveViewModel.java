package com.github.tvbox.osc.drive.viewmodel;

import androidx.lifecycle.ViewModel;

import com.github.tvbox.osc.drive.bean.DriveFolderFile;

import java.text.Collator;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public abstract class AbstractDriveViewModel extends ViewModel {

    protected DriveFolderFile currentDrive = null;
    protected DriveFolderFile currentDriveNote = null;
    protected int sortType = 0;

    public DriveFolderFile getCurrentDrive() {
        return currentDrive;
    }

    public void setCurrentDrive(DriveFolderFile currentDrive) {
        this.currentDrive = currentDrive;
    }

    public DriveFolderFile getCurrentDriveNote() {
        return currentDriveNote;
    }

    public void setCurrentDriveNote(DriveFolderFile currentDriveNote) {
        this.currentDriveNote = currentDriveNote;
    }

    public void setSortType(int sortType) {
        this.sortType = sortType;
    }

    /**
     * 取消进行中的网络请求（默认空实现，子类按需覆写）
     */
    public void cancel() {
    }

    public abstract String loadData(LoadDataCallback callback);

    protected void sortData(List<DriveFolderFile> data) {
        DriveFolderFile backItem = null;
        if (data.size() > 0 && data.get(0).name == null)
            backItem = data.remove(0);
        Collections.sort(data, sortComparator);
        if (backItem != null)
            data.add(0, backItem);
    }

    // [P0修复] 缓存 Collator 实例，避免每次 compare() 调用 getInstance()
    private final Collator chineseCollator = Collator.getInstance(Locale.CHINESE);

    private final Comparator<DriveFolderFile> sortComparator = new Comparator<DriveFolderFile>() {
        @Override
        public int compare(DriveFolderFile o1, DriveFolderFile o2) {
            switch (sortType) {
                case 1:
                    return chineseCollator.compare(o2.name.toUpperCase(Locale.CHINESE), o1.name.toUpperCase(Locale.CHINESE));
                case 2:
                    // [P0修复] lastModifiedDate 为 Long 包装类型，可能为 null
                    // 直接 Long.compare() 会触发自动拆箱，null 时抛 NullPointerException
                    return compareNullableLong(o1.lastModifiedDate, o2.lastModifiedDate, false);
                case 3:
                    return compareNullableLong(o1.lastModifiedDate, o2.lastModifiedDate, true);
                default:
                    return chineseCollator.compare(o1.name.toUpperCase(Locale.CHINESE), o2.name.toUpperCase(Locale.CHINESE));
            }
        }
    };

    /**
     * 比较 two nullable Long values. null 视为最小值（排到最后）。
     *
     * @param desc true=降序, false=升序
     */
    private static int compareNullableLong(Long a, Long b, boolean desc) {
        if (a == null && b == null) return 0;
        if (a == null) return desc ? -1 : 1;
        if (b == null) return desc ? 1 : -1;
        return desc ? Long.compare(b, a) : Long.compare(a, b);
    }

    public interface LoadDataCallback {
        void callback(List<DriveFolderFile> list, boolean alreadyHasChildren);
        void fail(String message);
    }
}