package com.github.tvbox.osc.drive.viewmodel;

import android.os.Handler;
import android.os.Looper;

import com.github.tvbox.osc.drive.bean.DriveFolderFile;
import com.github.tvbox.osc.drive.util.ArchiveHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 压缩包虚拟目录浏览 ViewModel。
 * <p>
 * 将 .zip / .7z 压缩包视为一个只读文件系统，
 * 通过前缀过滤在内存中模拟目录导航，无需实际解压。
 */
public class ArchiveBrowseViewModel extends AbstractDriveViewModel {

    private File archiveFile;
    private volatile List<ArchiveHelper.EntryInfo> allEntries;
    private String currentVirtualPath = "";   // 当前在包内的虚拟路径，如 "" / "folder1/" / "folder1/sub/"
    private Thread workThread;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ==================== 外部设置 ====================

    public void setArchiveFile(File file) {
        this.archiveFile = file;
        this.allEntries = null;
        this.currentVirtualPath = "";
    }

    public File getArchiveFile() {
        return archiveFile;
    }

    public String getCurrentVirtualPath() {
        return currentVirtualPath;
    }

    /** 导航到包内某个虚拟路径。 */
    public void navigateTo(String virtualPath) {
        this.currentVirtualPath = virtualPath;
    }

    /** 拼接条目在包内的完整路径。 */
    public String getFullPathForItem(String itemName) {
        String path = currentVirtualPath;
        if (!path.isEmpty() && !path.endsWith("/")) path += "/";
        return path + itemName;
    }

    // ==================== 生命周期 ====================

    @Override
    public void cancel() {
        if (workThread != null) {
            workThread.interrupt();
            workThread = null;
        }
    }

    // ==================== 核心：加载数据 ====================

    @Override
    public String loadData(LoadDataCallback callback) {
        if (allEntries == null) {
            // 首次加载：后台线程解析压缩包
            final String displayName = archiveFile.getName();
            workThread = new Thread(() -> {
                try {
                    List<ArchiveHelper.EntryInfo> entries = ArchiveHelper.listEntries(archiveFile);
                    allEntries = entries;

                    List<DriveFolderFile> items = buildItemsForPath("");
                    mainHandler.post(() -> finishLoad(items, callback, displayName));
                } catch (final Exception e) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.fail("无法读取压缩包: " + e.getMessage());
                    });
                }
            });
            workThread.start();
            return displayName;
        }

        // 后续导航：数据已在内存，直接构建
        List<DriveFolderFile> items = buildItemsForPath(currentVirtualPath);
        String title = buildTitle();
        finishLoad(items, callback, title);
        return title;
    }

    // ==================== 内部方法 ====================

    private void finishLoad(List<DriveFolderFile> items, LoadDataCallback callback, String title) {
        sortData(items);

        // 返回按钮
        DriveFolderFile backItem = new DriveFolderFile(null, null, 0, false, null, null);
        backItem.parentFolder = backItem;       // 自引用，标识为返回项
        items.add(0, backItem);

        // 设置父引用，供返回导航使用
        for (DriveFolderFile item : items) {
            if (item != backItem) {
                item.parentFolder = currentDriveNote;
            }
        }

        currentDriveNote.setChildren(items);
        if (callback != null) callback.callback(items, false);
    }

    private String buildTitle() {
        if (currentVirtualPath.isEmpty()) {
            return archiveFile.getName();
        }
        String display = currentVirtualPath;
        while (display.endsWith("/")) display = display.substring(0, display.length() - 1);
        return archiveFile.getName() + "/" + display;
    }

    /**
     * 根据当前虚拟路径，从全量条目中筛选出直接子项（一层深度）。
     * <p>
     * 例如 prefix="folder1/" 时，"folder1/a.txt" → 直接子文件，
     * "folder1/sub/b.txt" → 提取出子目录 "sub"。
     */
    private List<DriveFolderFile> buildItemsForPath(String prefix) {
        String np = prefix;
        if (!np.isEmpty() && !np.endsWith("/")) np += "/";

        Set<String> dirNames = new HashSet<>();
        List<DriveFolderFile> items = new ArrayList<>();

        for (ArchiveHelper.EntryInfo entry : allEntries) {
            if (!entry.path.startsWith(np)) continue;

            String remaining = entry.path.substring(np.length());
            if (remaining.isEmpty()) continue;

            int slashIdx = remaining.indexOf('/');
            if (slashIdx < 0) {
                // 直接子项
                if (entry.isFile) {
                    String name = remaining;
                    int extIdx = name.lastIndexOf('.');
                    String ext = (extIdx >= 0 && extIdx < name.length() - 1)
                            ? name.substring(extIdx + 1).toUpperCase(Locale.ROOT) : null;
                    items.add(new DriveFolderFile(null, name, 0, true, ext, entry.lastModified));
                }
                // 目录条目本身跳过（从文件路径推导目录）
            } else {
                // 属于某个子目录 → 提取目录名
                String dirName = remaining.substring(0, slashIdx);
                if (dirNames.add(dirName)) {
                    items.add(new DriveFolderFile(null, dirName, 0, false, null, 0L));
                }
            }
        }
        return items;
    }
}