package com.github.tvbox.osc.drive.util;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * 压缩包工具类，统一封装 ZIP / 7Z 的列表与解压操作。
 * 依赖 commons-compress + xz（纯 Java，无 native）。
 */
public class ArchiveHelper {

    // ==================== 判断 ====================

    public static boolean isArchiveFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) return false;
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".7z");
    }

    // ==================== 条目信息 ====================

    public static class EntryInfo {
        public final String path;         // 包内完整路径，如 "folder/file.txt"
        public final String name;         // 显示名，如 "file.txt"
        public final boolean isFile;
        public final long size;
        public final long lastModified;

        EntryInfo(String path, String name, boolean isFile, long size, long lastModified) {
            this.path = path;
            this.name = name;
            this.isFile = isFile;
            this.size = size;
            this.lastModified = lastModified;
        }
    }

    // ==================== 列表 ====================

    /**
     * 列出压缩包内所有条目。
     */
    public static List<EntryInfo> listEntries(File archiveFile) throws IOException {
        String lower = archiveFile.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            return listZipEntries(archiveFile);
        } else if (lower.endsWith(".7z")) {
            return list7zEntries(archiveFile);
        }
        throw new IOException("不支持的压缩格式: " + archiveFile.getName());
    }

    private static List<EntryInfo> listZipEntries(File archiveFile) throws IOException {
        List<EntryInfo> entries = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(archiveFile)) {
            Enumeration<ZipArchiveEntry> en = zipFile.getEntries();
            while (en.hasMoreElements()) {
                ZipArchiveEntry entry = en.nextElement();
                String raw = normalizePath(entry.getName());
                if (raw.isEmpty()) continue;

                String displayName = raw.endsWith("/")
                        ? raw.substring(0, raw.length() - 1)
                        : raw;
                int lastSep = displayName.lastIndexOf('/');
                displayName = lastSep >= 0 ? displayName.substring(lastSep + 1) : displayName;

                entries.add(new EntryInfo(
                        raw,
                        displayName,
                        !entry.isDirectory(),
                        entry.getSize(),
                        entry.getTime()
                ));
            }
        }
        return entries;
    }

    private static List<EntryInfo> list7zEntries(File archiveFile) throws IOException {
        List<EntryInfo> entries = new ArrayList<>();
        try (SevenZFile szf = new SevenZFile(archiveFile)) {
            SevenZArchiveEntry entry;
            while ((entry = szf.getNextEntry()) != null) {
                String raw = normalizePath(entry.getName());
                if (raw.isEmpty()) continue;

                String displayName = raw.endsWith("/")
                        ? raw.substring(0, raw.length() - 1)
                        : raw;
                int lastSep = displayName.lastIndexOf('/');
                displayName = lastSep >= 0 ? displayName.substring(lastSep + 1) : displayName;

                entries.add(new EntryInfo(
                        raw,
                        displayName,
                        !entry.isDirectory(),
                        entry.getSize(),
                        entry.getLastModifiedDate().getTime()
                ));
            }
        }
        return entries;
    }

    // ==================== 解压单个文件 ====================

    /**
     * 从压缩包中解压单个文件到 outputFile。
     *
     * @param entryPath  包内路径（必须与 listEntries 返回的 EntryInfo.path 一致）
     */
    public static void extractFile(File archiveFile, String entryPath,
                                   File outputFile, ProgressCallback callback) throws IOException {
        String lower = archiveFile.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            extractZipEntry(archiveFile, entryPath, outputFile, callback);
        } else if (lower.endsWith(".7z")) {
            extract7zEntry(archiveFile, entryPath, outputFile, callback);
        }
    }

    private static void extractZipEntry(File archiveFile, String entryPath,
                                        File outputFile, ProgressCallback cb) throws IOException {
        try (ZipFile zipFile = new ZipFile(archiveFile)) {
            ZipArchiveEntry entry = zipFile.getEntry(entryPath);
            if (entry == null) throw new IOException("压缩包内未找到: " + entryPath);

            outputFile.getParentFile().mkdirs();
            long total = entry.getSize();
            long copied = 0;

            try (InputStream is = zipFile.getInputStream(entry);
                 OutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) > 0) {
                    if (cb != null && cb.isCancelled()) { outputFile.delete(); return; }
                    os.write(buf, 0, len);
                    copied += len;
                    if (total > 0 && cb != null) cb.onProgress((int) (copied * 100 / total));
                }
            }
        }
    }

    private static void extract7zEntry(File archiveFile, String entryPath,
                                       File outputFile, ProgressCallback cb) throws IOException {
        try (SevenZFile szf = new SevenZFile(archiveFile)) {
            SevenZArchiveEntry entry;
            while ((entry = szf.getNextEntry()) != null) {
                String normalizedName = normalizePath(entry.getName());
                if (normalizedName.equals(entryPath)) {
                    outputFile.getParentFile().mkdirs();
                    long total = entry.getSize();
                    long copied = 0;

                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = szf.read(buf)) > 0) {
                            if (cb != null && cb.isCancelled()) { outputFile.delete(); return; }
                            os.write(buf, 0, len);
                            copied += len;
                            if (total > 0 && cb != null) cb.onProgress((int) (copied * 100 / total));
                        }
                    }
                    return;
                }
            }
            throw new IOException("压缩包内未找到: " + entryPath);
        }
    }

    // ==================== 解压全部 / 按前缀解压 ====================

    /**
     * 解压压缩包中所有文件到 outputDir。
     */
    public static void extractAll(File archiveFile, File outputDir,
                                  ProgressCallback callback) throws IOException {
        extractAll(archiveFile, outputDir, null, callback);
    }

    /**
     * 仅解压以 prefix 开头的条目，输出时去除 prefix 前缀。
     * 例如 prefix="folder1/" 时，"folder1/a.txt" → outputDir/a.txt。
     */
    public static void extractAll(File archiveFile, File outputDir,
                                  String prefix, ProgressCallback callback) throws IOException {
        String lower = archiveFile.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            extractAllZip(archiveFile, outputDir, prefix, callback);
        } else if (lower.endsWith(".7z")) {
            extractAll7z(archiveFile, outputDir, prefix, callback);
        }
    }

    private static void extractAllZip(File archiveFile, File outputDir,
                                      String prefix, ProgressCallback cb) throws IOException {
        try (ZipFile zipFile = new ZipFile(archiveFile)) {
            Enumeration<ZipArchiveEntry> en = zipFile.getEntries();
            while (en.hasMoreElements()) {
                if (cb != null && cb.isCancelled()) return;
                ZipArchiveEntry entry = en.nextElement();
                String raw = normalizePath(entry.getName());
                if (raw.isEmpty()) continue;
                if (prefix != null && !prefix.isEmpty()) {
                    if (!raw.startsWith(prefix)) continue;
                    raw = raw.substring(prefix.length());
                    if (raw.isEmpty()) continue;
                }
                if (entry.isDirectory()) {
                    new File(outputDir, raw).mkdirs();
                    continue;
                }
                if (cb != null) cb.onCurrentFile(entry.getName());

                File out = new File(outputDir, raw);
                out.getParentFile().mkdirs();
                try (InputStream is = zipFile.getInputStream(entry);
                     OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
                }
            }
            if (cb != null) cb.onProgress(100);
        }
    }

    private static void extractAll7z(File archiveFile, File outputDir,
                                     String prefix, ProgressCallback cb) throws IOException {
        try (SevenZFile szf = new SevenZFile(archiveFile)) {
            SevenZArchiveEntry entry;
            while ((entry = szf.getNextEntry()) != null) {
                if (cb != null && cb.isCancelled()) return;
                String raw = normalizePath(entry.getName());
                if (raw.isEmpty()) continue;
                if (prefix != null && !prefix.isEmpty()) {
                    if (!raw.startsWith(prefix)) continue;
                    raw = raw.substring(prefix.length());
                    if (raw.isEmpty()) continue;
                }
                if (entry.isDirectory()) {
                    new File(outputDir, raw).mkdirs();
                    continue;
                }
                if (cb != null) cb.onCurrentFile(entry.getName());

                File out = new File(outputDir, raw);
                out.getParentFile().mkdirs();
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = szf.read(buf)) > 0) os.write(buf, 0, len);
                }
            }
            if (cb != null) cb.onProgress(100);
        }
    }

    // ==================== 进度回调 ====================

    public interface ProgressCallback {
        void onProgress(int percent);
        void onCurrentFile(String fileName);
        boolean isCancelled();
    }

    // ==================== 内部工具 ====================

    private static String normalizePath(String path) {
        String p = path.replace("\\", "/");
        while (p.startsWith("/")) p = p.substring(1);
        return p;
    }
}