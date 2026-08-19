package com.github.tvbox.osc.drive.viewmodel;

import com.github.tvbox.osc.drive.bean.DriveFolderFile;
import com.google.gson.JsonObject;

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2Dialect;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * SMB 存储 ViewModel。
 * SMB 1.0 使用 JCIFS-NG 库（eu.agno3.jcifs:jcifs-ng）。
 * SMB 2.0 / 3.0 使用 SMBJ 库（com.hierynomus:smbj）。
 * 版本映射：0=自动, 1=SMB 1.0, 2=SMB 2.0, 3=SMB 3.0
 */
public class SmbDriveViewModel extends AbstractDriveViewModel {

    // ===== SMBJ（SMB 2.0/3.0）字段 =====
    private SMBClient smbClient;
    private Connection smbConnection;
    private Session smbSession;
    private DiskShare smbShare;

    // ===== JCIFS-NG（SMB 1.0）字段 =====
    private CIFSContext cifsContext;
    private SmbFile cifsBaseDir;

    // [P2修复] 添加 volatile，确保 cancel()（主线程）与 loadData（工作线程）之间的可见性
    private volatile Thread workThread;
    private boolean useSmb1 = false; // 标记当前是否使用 SMB 1.0

    @Override
    public void cancel() {
        if (workThread != null) {
            workThread.interrupt();
            workThread = null;
        }
        disconnectAll();
    }

    /**
     * 根据配置判断是否应使用 SMB 1.0。
     */
    private boolean shouldUseSmb1(JsonObject config) {
        int smbVersion = config.has("smbVersion") ? config.get("smbVersion").getAsInt() : 0;
        return smbVersion == 1; // 仅明确选择 SMB 1.0 时使用 jcifs-ng
    }

    // ==================== SMBJ 连接（SMB 2.0/3.0）====================

    private boolean connectSmbJ(JsonObject config) {
        if (smbShare != null) return true;
        try {
            String host = config.get("host").getAsString();
            int port = config.has("port") ? config.get("port").getAsInt() : 445;
            String shareName = config.get("shareName").getAsString();
            String domain = config.has("domain") ? config.get("domain").getAsString() : "";
            String username = config.has("username") ? config.get("username").getAsString() : "";
            String password = config.has("password") ? config.get("password").getAsString() : "";
            int smbVersion = config.has("smbVersion") ? config.get("smbVersion").getAsInt() : 0;

            SmbConfig.Builder configBuilder = SmbConfig.builder();
            switch (smbVersion) {
                case 2: // SMB 2.0
                    configBuilder.withDialects(SMB2Dialect.SMB_2_0_2, SMB2Dialect.SMB_2_1);
                    break;
                case 3: // SMB 3.0
                    configBuilder.withDialects(SMB2Dialect.SMB_3_0, SMB2Dialect.SMB_3_0_2, SMB2Dialect.SMB_3_1_1);
                    break;
                default: // 自动 — 协商最高版本
                    break;
            }

            smbClient = new SMBClient(configBuilder.build());
            smbConnection = smbClient.connect(host, port);
            AuthenticationContext ac = new AuthenticationContext(username, password.toCharArray(), domain);
            smbSession = smbConnection.authenticate(ac);
            smbShare = (DiskShare) smbSession.connectShare(shareName);
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            disconnectSmbJ();
            return false;
        }
    }

    private void disconnectSmbJ() {
        try { if (smbShare != null) smbShare.close(); } catch (Exception ignored) {}
        smbShare = null;
        try { if (smbSession != null) smbSession.close(); } catch (Exception ignored) {}
        smbSession = null;
        try { if (smbConnection != null) smbConnection.close(); } catch (Exception ignored) {}
        smbConnection = null;
        // [P1修复] SMBClient 内部有线程池，必须 close() 释放，否则线程泄漏
        try { if (smbClient != null) smbClient.close(); } catch (Exception ignored) {}
        smbClient = null;
    }

    // ==================== JCIFS-NG 连接（SMB 1.0）====================

    private boolean connectCifs(JsonObject config) {
        if (cifsBaseDir != null) return true;
        try {
            String host = config.get("host").getAsString();
            int port = config.has("port") ? config.get("port").getAsInt() : 445;
            String shareName = config.get("shareName").getAsString();
            String domain = config.has("domain") ? config.get("domain").getAsString() : "";
            String username = config.has("username") ? config.get("username").getAsString() : "";
            String password = config.has("password") ? config.get("password").getAsString() : "";

            Properties props = new Properties();
            // 禁用 SMB 2/3 协商，强制使用 SMB 1.0
            props.setProperty("jcifs.smb.client.useExtendedSecurity", "false");
            props.setProperty("jcifs.smb.client.responseTimeout", "10000");
            props.setProperty("jcifs.smb.client.soTimeout", "10000");

            PropertyConfiguration pc = new PropertyConfiguration(props);
            BaseContext bc = new BaseContext(pc);

            NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator(
                    domain.isEmpty() ? null : domain, username, password);
            cifsContext = bc.withCredentials(auth);

            String url = "smb://" + host + ":" + port + "/" + shareName + "/";
            cifsBaseDir = new SmbFile(url, cifsContext);
            cifsBaseDir.connect(); // 测试连接
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            disconnectCifs();
            return false;
        }
    }

    private void disconnectCifs() {
        try { if (cifsBaseDir != null) cifsBaseDir.close(); } catch (Exception ignored) {}
        cifsBaseDir = null;
        cifsContext = null;
    }

    private void disconnectAll() {
        disconnectSmbJ();
        disconnectCifs();
    }

    // ==================== 数据加载 ====================

    @Override
    public String loadData(LoadDataCallback callback) {
        JsonObject config = currentDrive.getConfig();
        useSmb1 = shouldUseSmb1(config);

        if (currentDriveNote == null) {
            currentDriveNote = new DriveFolderFile(null,
                    config.has("initPath") ? config.get("initPath").getAsString() : "",
                    0, false, null, null);
        }

        String targetPath = currentDriveNote.getAccessingPathStr() + currentDriveNote.name;
        // 统一使用正斜杠，去掉首尾斜杠
        targetPath = targetPath.replace("\\", "/");
        while (targetPath.startsWith("/")) targetPath = targetPath.substring(1);
        while (targetPath.length() > 0 && targetPath.endsWith("/"))
            targetPath = targetPath.substring(0, targetPath.length() - 1);

        final String listPath = targetPath;

        if (currentDriveNote.getChildren() == null) {
            workThread = new Thread(() -> {
                try {
                    if (useSmb1) {
                        loadWithCifs(config, listPath, callback);
                    } else {
                        loadWithSmbJ(config, listPath, callback);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    if (callback != null)
                        callback.fail(useSmb1 ? "CIFS访问失败: " + ex.getMessage()
                                : "SMB访问失败: " + ex.getMessage());
                    disconnectAll();
                }
            });
            workThread.start();
            return targetPath;
        } else {
            sortData(currentDriveNote.getChildren());
            if (callback != null) callback.callback(currentDriveNote.getChildren(), true);
        }
        return targetPath;
    }

    /**
     * 使用 SMBJ（SMB 2.0/3.0）加载目录列表。
     */
    private void loadWithSmbJ(JsonObject config, String listPath, LoadDataCallback callback) {
        try {
            if (!connectSmbJ(config)) {
                if (callback != null) callback.fail("无法连接SMB服务器");
                return;
            }

            List<FileIdBothDirectoryInformation> entries = smbShare.list(listPath);
            List<DriveFolderFile> items = new ArrayList<>();

            for (FileIdBothDirectoryInformation info : entries) {
                String fileName = info.getFileName();
                if (".".equals(fileName) || "..".equals(fileName)) continue;

                boolean isFile = (info.getFileAttributes() & 0x10L) == 0;
                int extIdx = fileName.lastIndexOf(".");
                String ext = (isFile && extIdx >= 0 && extIdx < fileName.length() - 1)
                        ? fileName.substring(extIdx + 1) : null;

                long lastModified = 0;
                try {
                    if (info.getLastWriteTime() != null) {
                        lastModified = info.getLastWriteTime().toEpochMillis();
                    }
                } catch (Exception ignored) {}

                items.add(new DriveFolderFile(currentDriveNote, fileName, 0, isFile, ext, lastModified));
            }

            sortData(items);
            DriveFolderFile backItem = new DriveFolderFile(null, null, 0, false, null, null);
            backItem.parentFolder = backItem;
            items.add(0, backItem);
            currentDriveNote.setChildren(items);
            if (callback != null) callback.callback(items, false);

        } catch (Exception ex) {
            ex.printStackTrace();
            if (callback != null) callback.fail("SMB访问失败: " + ex.getMessage());
            disconnectSmbJ();
        }
    }

    /**
     * 使用 JCIFS-NG（SMB 1.0）加载目录列表。
     */
    private void loadWithCifs(JsonObject config, String listPath, LoadDataCallback callback) {
        try {
            if (!connectCifs(config)) {
                if (callback != null) callback.fail("无法连接SMB 1.0服务器");
                return;
            }

            // 拼接完整路径：smb://host:port/shareName/listPath
            String fullPath = listPath.isEmpty() ? "" : listPath;
            SmbFile targetDir;
            if (fullPath.isEmpty()) {
                targetDir = cifsBaseDir;
            } else {
                targetDir = new SmbFile(cifsBaseDir, fullPath + "/");
            }

            if (!targetDir.exists() || !targetDir.isDirectory()) {
                if (callback != null) callback.fail("目录不存在: " + fullPath);
                return;
            }

            SmbFile[] resources = targetDir.listFiles();
            List<DriveFolderFile> items = new ArrayList<>();

            if (resources != null) {
                for (SmbFile res : resources) {
                    String fileName = res.getName();
                    // JCIFS 返回的名称可能带尾部斜杠（目录）
                    if (fileName.endsWith("/")) {
                        fileName = fileName.substring(0, fileName.length() - 1);
                    }
                    if (fileName.isEmpty()) continue;

                    boolean isFile = res.isFile();
                    int extIdx = fileName.lastIndexOf(".");
                    String ext = (isFile && extIdx >= 0 && extIdx < fileName.length() - 1)
                            ? fileName.substring(extIdx + 1) : null;

                    long lastModified = 0;
                    try {
                        lastModified = res.getLastModified();
                    } catch (Exception ignored) {}

                    items.add(new DriveFolderFile(currentDriveNote, fileName, 0, isFile, ext, lastModified));
                }
            }

            sortData(items);
            DriveFolderFile backItem = new DriveFolderFile(null, null, 0, false, null, null);
            backItem.parentFolder = backItem;
            items.add(0, backItem);
            currentDriveNote.setChildren(items);
            if (callback != null) callback.callback(items, false);

        } catch (Exception ex) {
            ex.printStackTrace();
            if (callback != null) callback.fail("SMB 1.0访问失败: " + ex.getMessage());
            disconnectCifs();
        }
    }

    /**
     * 构建 SMB 文件 URL：smb://[user:pass@]host[:port]/shareName/path
     */
    public static String buildSmbFileUrl(JsonObject config, String smbPath) {
        String host = config.get("host").getAsString();
        int port = config.has("port") ? config.get("port").getAsInt() : 445;
        String username = config.has("username") ? config.get("username").getAsString() : "";
        String password = config.has("password") ? config.get("password").getAsString() : "";

        StringBuilder sb = new StringBuilder("smb://");
        if (username != null && username.length() > 0) {
            sb.append(username).append(":").append(password).append("@");
        }
        sb.append(host);
        if (port != 445) sb.append(":").append(port);
        if (!smbPath.startsWith("/")) sb.append("/");
        sb.append(smbPath);
        return sb.toString();
    }
}