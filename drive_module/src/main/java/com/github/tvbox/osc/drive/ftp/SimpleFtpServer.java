package com.github.tvbox.osc.drive.ftp;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * 轻量级嵌入式 FTP 服务器。
 * <p>
 * 基于 Java ServerSocket 实现，无需任何第三方依赖。
 * 支持 PASV 被动模式、匿名/用户名密码认证、目录浏览和文件下载。
 * <p>
 * 使用方式：
 * <pre>
 *   SimpleFtpServer server = SimpleFtpServer.getInstance();
 *   server.start(3721, "admin", "123456", "/sdcard/Movies");
 *   String url = server.getFtpUrl(); // "ftp://192.168.1.13:3721/"
 *   server.stop();
 * </pre>
 */
public class SimpleFtpServer {

    private static final String TAG = "SimpleFtpServer";
    private static final int DATA_TIMEOUT = 15000;
    private static final int CONTROL_IDLE_TIMEOUT = 300000;

    private static volatile SimpleFtpServer instance;

    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private int port = 3721;
    private String username = "";
    private String password = "";
    private File rootDir;
    private Thread acceptThread;
    private String localIp = "";

    private SimpleFtpServer() {
    }

    public static SimpleFtpServer getInstance() {
        if (instance == null) {
            synchronized (SimpleFtpServer.class) {
                if (instance == null) {
                    instance = new SimpleFtpServer();
                }
            }
        }
        return instance;
    }

    // ==================== 生命周期 ====================

    /**
     * 启动 FTP 服务器。
     *
     * @param port     监听端口（推荐 3721，避免与系统 FTP 冲突）
     * @param username 用户名（空字符串表示匿名登录）
     * @param password 密码
     * @param rootPath 根目录绝对路径
     * @return true 表示启动成功
     */
    public synchronized boolean start(int port, String username, String password, String rootPath) {
        if (running) {
            stop();
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }

        File root = new File(rootPath);
        if (!root.exists() || !root.isDirectory()) {
            Log.e(TAG, "根目录不存在: " + rootPath);
            return false;
        }

        try {
            this.port = port;
            this.username = (username != null) ? username : "";
            this.password = (password != null) ? password : "";
            this.rootDir = root;
            this.localIp = getLocalIpAddress();

            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            running = true;

            acceptThread = new Thread(this::acceptLoop, "FtpServer-Accept");
            acceptThread.setDaemon(true);
            acceptThread.start();

            Log.i(TAG, "FTP 服务器已启动: " + getFtpUrl() + " 根目录=" + rootPath);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "FTP 服务器启动失败", e);
            running = false;
            return false;
        }
    }

    /**
     * 停止 FTP 服务器，关闭所有连接。
     */
    public synchronized void stop() {
        if (!running) return;
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        serverSocket = null;
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
        Log.i(TAG, "FTP 服务器已停止");
    }

    public boolean isRunning() { return running; }
    public int getPort() { return port; }
    public String getLocalIp() { return localIp; }

    /**
     * 获取 FTP 访问地址，如 "ftp://192.168.1.13:3721/"
     */
    public String getFtpUrl() {
        if (!running || localIp.isEmpty()) return "";
        return "ftp://" + localIp + ":" + port + "/";
    }

    // ==================== 主接受循环 ====================

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                Thread t = new Thread(() -> handleClient(client),
                        "FtpClient-" + client.getRemoteSocketAddress());
                t.setDaemon(true);
                t.start();
            } catch (SocketException e) {
                break;
            } catch (IOException e) {
                if (running) Log.e(TAG, "接受客户端连接异常", e);
                break;
            }
        }
    }

    // ==================== 单客户端处理 ====================

    private void handleClient(Socket clientSocket) {
        Socket dataSocket = null;
        ServerSocket passiveServer = null;
        try {
            clientSocket.setSoTimeout(CONTROL_IDLE_TIMEOUT);
            InputStream rawIn = clientSocket.getInputStream();
            OutputStream rawOut = clientSocket.getOutputStream();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new InputStreamReader(rawIn, "UTF-8"));

            send(rawOut, "220 FTP Server Ready");

            boolean authenticated = (this.username.isEmpty());
            String currentUser = "";
            String currentPath = "/";
            boolean binaryMode = true;

            String line;
            while (running) {
                try {
                    line = reader.readLine();
                } catch (SocketTimeoutException e) {
                    break;
                }
                if (line == null) break;
                if (Thread.currentThread().isInterrupted()) break;

                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(" ", 2);
                String cmd = parts[0].toUpperCase(Locale.ROOT);
                String arg = parts.length > 1 ? parts[1].trim() : "";

                if (!authenticated && !"USER".equals(cmd) && !"PASS".equals(cmd) && !"QUIT".equals(cmd)) {
                    send(rawOut, "530 请先登录");
                    continue;
                }

                switch (cmd) {
                    case "USER":
                        if (this.username.isEmpty()) {
                            authenticated = true;
                            send(rawOut, "230 匿名登录成功");
                        } else {
                            currentUser = arg;
                            send(rawOut, "331 请输入密码");
                        }
                        break;

                    case "PASS":
                        if (this.username.isEmpty()) {
                            send(rawOut, "230 已登录");
                        } else if (arg.equals(this.password) && currentUser.equals(this.username)) {
                            authenticated = true;
                            send(rawOut, "230 登录成功");
                        } else {
                            send(rawOut, "530 用户名或密码错误");
                        }
                        break;

                    case "PWD":
                    case "XPWD":
                        send(rawOut, "257 \"" + currentPath + "\" 是当前目录");
                        break;

                    case "CWD": {
                        String newPath = resolvePath(currentPath, arg);
                        File dir = resolveFile(newPath);
                        if (dir != null && dir.isDirectory()) {
                            currentPath = normalizePath(newPath);
                            send(rawOut, "250 目录已切换到 " + currentPath);
                        } else {
                            send(rawOut, "550 目录不存在");
                        }
                        break;
                    }

                    case "CDUP": {
                        String parentPath = getParentPath(currentPath);
                        File dir = resolveFile(parentPath);
                        if (dir != null && dir.isDirectory()) {
                            currentPath = parentPath;
                            send(rawOut, "250 目录已切换到 " + currentPath);
                        } else {
                            send(rawOut, "550 已在根目录");
                        }
                        break;
                    }

                    case "TYPE":
                        if (arg.toUpperCase(Locale.ROOT).startsWith("I")) {
                            binaryMode = true;
                            send(rawOut, "200 类型设置为二进制");
                        } else if (arg.toUpperCase(Locale.ROOT).startsWith("A")) {
                            binaryMode = false;
                            send(rawOut, "200 类型设置为 ASCII");
                        } else {
                            send(rawOut, "504 不支持的类型");
                        }
                        break;

                    case "PASV": {
                        closePassive(passiveServer);
                        passiveServer = null;
                        try {
                            passiveServer = new ServerSocket(0);
                            passiveServer.setReuseAddress(true);
                            passiveServer.setSoTimeout(DATA_TIMEOUT);
                            int dataPort = passiveServer.getLocalPort();
                            String ipStr = localIp.replace(".", ",");
                            int p1 = dataPort / 256;
                            int p2 = dataPort % 256;
                            send(rawOut, "227 进入被动模式 (" + ipStr + "," + p1 + "," + p2 + ")");
                        } catch (IOException e) {
                            send(rawOut, "425 无法打开数据连接");
                            closePassive(passiveServer);
                            passiveServer = null;
                        }
                        break;
                    }

                    case "LIST":
                    case "NLST": {
                        if (passiveServer == null) {
                            send(rawOut, "425 请先使用 PASV");
                            break;
                        }
                        File listDir = resolveFile(currentPath);
                        if (listDir == null || !listDir.isDirectory()) {
                            send(rawOut, "550 目录不存在");
                            closePassive(passiveServer);
                            passiveServer = null;
                            break;
                        }
                        send(rawOut, "150 正在打开数据连接");
                        boolean isList = "LIST".equals(cmd);
                        try {
                            dataSocket = passiveServer.accept();
                            OutputStream dataOut = dataSocket.getOutputStream();
                            File[] files = listDir.listFiles();
                            if (files != null) {
                                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd  HH:mm", Locale.ENGLISH);
                                for (File f : files) {
                                    if (f.isHidden()) continue;
                                    String entry;
                                    if (f.isDirectory()) {
                                        entry = "drwxr-xr-x 1 owner group          0 "
                                                + sdf.format(new Date(f.lastModified())) + " " + f.getName();
                                    } else {
                                        entry = "-rw-r--r-- 1 owner group " + padLeft(f.length(), 12)
                                                + " " + sdf.format(new Date(f.lastModified())) + " " + f.getName();
                                    }
                                    if (isList) {
                                        dataOut.write((entry + "\r\n").getBytes("UTF-8"));
                                    } else {
                                        dataOut.write((f.getName() + "\r\n").getBytes("UTF-8"));
                                    }
                                }
                            }
                            dataOut.flush();
                            dataSocket.close();
                            dataSocket = null;
                            send(rawOut, "226 传输完成");
                        } catch (SocketTimeoutException e) {
                            send(rawOut, "425 数据连接超时");
                        } finally {
                            closeQuietly(dataSocket);
                            dataSocket = null;
                            closePassive(passiveServer);
                            passiveServer = null;
                        }
                        break;
                    }

                    case "RETR": {
                        if (passiveServer == null) {
                            send(rawOut, "425 请先使用 PASV");
                            break;
                        }
                        String filePath = resolvePath(currentPath, arg);
                        File file = resolveFile(filePath);
                        if (file == null || !file.exists() || file.isDirectory()) {
                            send(rawOut, "550 文件不存在");
                            closePassive(passiveServer);
                            passiveServer = null;
                            break;
                        }
                        send(rawOut, "150 正在打开数据连接 (" + file.length() + " 字节)");
                        // [P1修复] 使用 try-with-resources 确保 FileInputStream 在异常路径也被关闭
                        try (FileInputStream fis = new FileInputStream(file)) {
                            dataSocket = passiveServer.accept();
                            dataSocket.setSoTimeout(DATA_TIMEOUT);
                            OutputStream dataOut = dataSocket.getOutputStream();
                            byte[] buffer = new byte[32768];
                            int len;
                            while ((len = fis.read(buffer)) != -1) {
                                dataOut.write(buffer, 0, len);
                            }
                            dataOut.flush();
                            dataSocket.close();
                            dataSocket = null;
                            send(rawOut, "226 传输完成");
                        } catch (SocketTimeoutException e) {
                            send(rawOut, "426 数据连接超时");
                        } finally {
                            closeQuietly(dataSocket);
                            dataSocket = null;
                            closePassive(passiveServer);
                            passiveServer = null;
                        }
                        break;
                    }

                    case "SIZE": {
                        String filePath = resolvePath(currentPath, arg);
                        File file = resolveFile(filePath);
                        if (file != null && file.exists() && file.isFile()) {
                            send(rawOut, "213 " + file.length());
                        } else {
                            send(rawOut, "550 文件不存在");
                        }
                        break;
                    }

                    case "SYST":
                        send(rawOut, "215 UNIX Type: L8");
                        break;

                    case "FEAT":
                        send(rawOut, "211-Features:");
                        send(rawOut, " PASV");
                        send(rawOut, " UTF8");
                        send(rawOut, " SIZE");
                        send(rawOut, "211 End");
                        break;

                    case "OPTS":
                        send(rawOut, "200 OK");
                        break;

                    case "PORT":
                        send(rawOut, "502 不支持主动模式，请使用 PASV");
                        break;

                    case "MKD":
                    case "RMD":
                    case "DELE":
                    case "STOR":
                    case "APPE":
                        send(rawOut, "502 只读服务器，不支持写操作");
                        break;

                    case "QUIT":
                        send(rawOut, "221 再见");
                        return;

                    default:
                        send(rawOut, "502 命令未实现: " + cmd);
                        break;
                }
            }
        } catch (IOException e) {
            if (running) Log.e(TAG, "处理客户端异常", e);
        } finally {
            closeQuietly(dataSocket);
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    // ==================== 路径处理 ====================

    private String resolvePath(String currentPath, String arg) {
        if (arg.startsWith("/")) {
            return normalizePath(arg);
        }
        if (currentPath.endsWith("/")) {
            return normalizePath(currentPath + arg);
        }
        return normalizePath(currentPath + "/" + arg);
    }

    private String normalizePath(String path) {
        String[] segments = path.split("/");
        List<String> clean = new ArrayList<>();
        for (String seg : segments) {
            if (seg.isEmpty() || ".".equals(seg)) continue;
            if ("..".equals(seg)) {
                if (!clean.isEmpty()) clean.remove(clean.size() - 1);
            } else {
                clean.add(seg);
            }
        }
        StringBuilder sb = new StringBuilder("/");
        for (int i = 0; i < clean.size(); i++) {
            if (i > 0) sb.append("/");
            sb.append(clean.get(i));
        }
        return sb.toString();
    }

    private String getParentPath(String path) {
        if ("/".equals(path)) return "/";
        int idx = path.lastIndexOf('/');
        if (idx <= 0) return "/";
        return path.substring(0, idx);
    }

    /**
     * 将 FTP 路径解析为 File，包含防目录穿越安全检查。
     */
    private File resolveFile(String ftpPath) {
        String[] segments = ftpPath.split("/");
        File file = rootDir;
        for (String seg : segments) {
            if (seg.isEmpty() || ".".equals(seg)) continue;
            if ("..".equals(seg)) {
                File parent = file.getParentFile();
                if (parent != null) {
                    try {
                        if (parent.getCanonicalPath().length() >= rootDir.getCanonicalPath().length()) {
                            file = parent;
                        }
                    } catch (IOException e) { return null; }
                }
                continue;
            }
            file = new File(file, seg);
        }
        try {
            String canonicalFile = file.getCanonicalPath();
            String canonicalRoot = rootDir.getCanonicalPath();
            if (!canonicalFile.startsWith(canonicalRoot)) return null;
        } catch (IOException e) { return null; }
        return file;
    }

    // ==================== 工具方法 ====================

    private void send(OutputStream out, String msg) throws IOException {
        out.write((msg + "\r\n").getBytes("UTF-8"));
        out.flush();
    }

    private String padLeft(long value, int width) {
        String s = String.valueOf(value);
        if (s.length() < width) {
            StringBuilder sb = new StringBuilder();
            for (int i = s.length(); i < width; i++) sb.append(' ');
            return sb.append(s).toString();
        }
        return s;
    }

    private void closePassive(ServerSocket ss) {
        if (ss != null) { try { ss.close(); } catch (IOException ignored) {} }
    }

    private void closeQuietly(Socket s) {
        if (s != null) { try { s.close(); } catch (IOException ignored) {} }
    }

    /**
     * 获取本机局域网 IPv4 地址。
     */
    static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLinkLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "获取本机 IP 失败", e);
        }
        return "127.0.0.1";
    }
}