package com.github.tvbox.osc.drive.viewmodel;

import com.github.tvbox.osc.drive.bean.DriveFolderFile;
import com.google.gson.JsonObject;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * FTP 存储 ViewModel。
 * 依赖 commons-net 的 FTPClient。
 */
public class FtpDriveViewModel extends AbstractDriveViewModel {

    private FTPClient ftpClient;
    private Thread workThread;

    @Override
    public void cancel() {
        if (workThread != null) {
            workThread.interrupt();
            workThread = null;
        }
        disconnectFtp();
    }

    private boolean connectFtp(JsonObject config) {
        if (ftpClient != null && ftpClient.isConnected()) return true;
        try {
            ftpClient = new FTPClient();
            ftpClient.setDefaultTimeout(10000);
            ftpClient.setConnectTimeout(10000);
            ftpClient.setDataTimeout(10000);

            String host = config.get("host").getAsString();
            int port = config.has("port") ? config.get("port").getAsInt() : 21;
            String username = config.has("username") ? config.get("username").getAsString() : "";
            String password = config.has("password") ? config.get("password").getAsString() : "";

            ftpClient.connect(host, port);
            if (username != null && username.length() > 0) {
                ftpClient.login(username, password);
            }
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            String encoding = config.has("encoding") ? config.get("encoding").getAsString() : "UTF-8";
            ftpClient.setControlEncoding(encoding);
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            disconnectFtp();
            return false;
        }
    }

    private void disconnectFtp() {
        if (ftpClient != null) {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (Exception ignored) {
            }
            ftpClient = null;
        }
    }

    @Override
    public String loadData(LoadDataCallback callback) {
        JsonObject config = currentDrive.getConfig();
        if (currentDriveNote == null) {
            currentDriveNote = new DriveFolderFile(null,
                    config.has("initPath") ? config.get("initPath").getAsString() : "", 0, false, null, null);
        }
        String targetPath = currentDriveNote.getAccessingPathStr() + currentDriveNote.name;
        // FTP 路径以 / 开头
        String ftpPath = targetPath.isEmpty() ? "/" : (targetPath.startsWith("/") ? targetPath : "/" + targetPath);

        if (currentDriveNote.getChildren() == null) {
            workThread = new Thread(() -> {
                try {
                    if (!connectFtp(config)) {
                        if (callback != null) callback.fail("无法连接FTP服务器");
                        return;
                    }

                    FTPFile[] ftpFiles = ftpClient.listFiles(ftpPath);
                    if (ftpFiles == null) {
                        if (callback != null) callback.fail("无法列出FTP目录: " + ftpPath);
                        disconnectFtp();
                        return;
                    }

                    List<DriveFolderFile> items = new ArrayList<>();
                    for (FTPFile ftpFile : ftpFiles) {
                        String fileName = ftpFile.getName();
                        // 跳过 . 和 ..
                        if (".".equals(fileName) || "..".equals(fileName)) continue;

                        boolean isFile = ftpFile.isFile();
                        int extNameStartIndex = fileName.lastIndexOf(".");
                        // [P2修复] FTPFile.getTimestamp() 可能返回 null，某些 FTP 服务器不提供时间戳
                        long lastModified = 0;
                        try {
                            if (ftpFile.getTimestamp() != null) {
                                lastModified = ftpFile.getTimestamp().getTimeInMillis();
                            }
                        } catch (Exception ignored) {}
                        items.add(new DriveFolderFile(currentDriveNote, fileName, 0, isFile,
                                isFile && extNameStartIndex >= 0 && extNameStartIndex < fileName.length() ?
                                        fileName.substring(extNameStartIndex + 1) : null,
                                lastModified));
                    }

                    sortData(items);
                    DriveFolderFile backItem = new DriveFolderFile(null, null, 0, false, null, null);
                    backItem.parentFolder = backItem;
                    items.add(0, backItem);
                    currentDriveNote.setChildren(items);
                    if (callback != null) callback.callback(items, false);

                    // 列完目录后不断开，返回上级时复用连接
                } catch (Exception ex) {
                    ex.printStackTrace();
                    if (callback != null) callback.fail("FTP访问失败: " + ex.getMessage());
                    disconnectFtp();
                }
            });
            workThread.start();
            return ftpPath;
        } else {
            sortData(currentDriveNote.getChildren());
            if (callback != null) callback.callback(currentDriveNote.getChildren(), true);
        }
        return ftpPath;
    }

    /**
     * 获取 FTP 文件的完整 URL（ftp://user:pass@host:port/path）。
     */
    public static String buildFtpFileUrl(JsonObject config, String targetPath) {
        String host = config.get("host").getAsString();
        int port = config.has("port") ? config.get("port").getAsInt() : 21;
        String username = config.has("username") ? config.get("username").getAsString() : "";
        String password = config.has("password") ? config.get("password").getAsString() : "";

        StringBuilder sb = new StringBuilder("ftp://");
        if (username != null && username.length() > 0) {
            sb.append(username).append(":").append(password).append("@");
        }
        sb.append(host);
        if (port != 21) sb.append(":").append(port);
        if (!targetPath.startsWith("/")) sb.append("/");
        sb.append(targetPath);
        return sb.toString();
    }
}