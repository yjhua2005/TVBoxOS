package com.github.tvbox.osc.drive.util;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * SMB mDNS 自动发现工具。
 * <p>
 * 利用 Android 原生 NsdManager 扫描局域网中的 _smb._tcp 服务，
 * 发现后解析出主机名和 IP 地址，供 SmbDriveDialog 使用。
 * <p>
 * 典型用法：
 * <pre>
 *   SmbDiscoveryUtil.discover(context, 5000, devices -> {
 *       // devices 包含 name / host / port
 *   });
 * </pre>
 */
public class SmbDiscoveryUtil {

    private static final String TAG = "SmbDiscovery";
    private static final String SERVICE_TYPE = "_smb._tcp";

    public static class DiscoveredDevice {
        public final String name;   // mDNS 广播的服务名（如 "MY-NAS"）
        public final String host;   // 解析后的 IP 地址
        public final int port;      // 端口（通常 445）

        public DiscoveredDevice(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }

        @Override
        public String toString() {
            return name + " (" + host + ":" + port + ")";
        }
    }

    public interface DiscoveryListener {
        /** 发现完成（超时或主动停止后回调） */
        void onDevicesFound(List<DiscoveredDevice> devices);
    }

    /**
     * 启动 mDNS 扫描，超时后自动停止并回调。
     *
     * @param context   Context
     * @param timeoutMs 扫描持续时间（毫秒），建议 3000-8000
     * @param listener  回调
     * @return NsdManager.DiscoveryListener，可用于提前调用 stopDiscovery
     */
    public static NsdManager.DiscoveryListener discover(Context context, int timeoutMs,
                                                         DiscoveryListener listener) {
        NsdManager nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (nsdManager == null) {
            Log.w(TAG, "NsdManager 不可用");
            if (listener != null) listener.onDevicesFound(new ArrayList<>());
            return null;
        }

        List<DiscoveredDevice> foundDevices = new ArrayList<>();
        Handler handler = new Handler(Looper.getMainLooper());

        NsdManager.DiscoveryListener discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "mDNS 发现已启动: " + regType);
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "发现服务: " + serviceInfo.getServiceName());
                // 异步解析地址
                nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                        Log.w(TAG, "解析失败: " + serviceInfo.getServiceName() + " code=" + errorCode);
                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo serviceInfo) {
                        InetAddress host = serviceInfo.getHost();
                        if (host == null) return;
                        String hostStr = host.getHostAddress();
                        int port = serviceInfo.getPort();
                        if (port <= 0) port = 445;

                        // 去重
                        synchronized (foundDevices) {
                            for (DiscoveredDevice d : foundDevices) {
                                if (d.host.equals(hostStr)) return;
                            }
                            DiscoveredDevice device = new DiscoveredDevice(
                                    serviceInfo.getServiceName(), hostStr, port);
                            foundDevices.add(device);
                            Log.d(TAG, "解析成功: " + device);
                        }
                    }
                });
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "服务消失: " + serviceInfo.getServiceName());
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "mDNS 发现已停止");
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.w(TAG, "启动发现失败: " + serviceType + " code=" + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.w(TAG, "停止发现失败: " + serviceType + " code=" + errorCode);
            }
        };

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (Exception e) {
            Log.e(TAG, "启动 mDNS 发现异常", e);
            if (listener != null) listener.onDevicesFound(new ArrayList<>());
            return null;
        }

        // 超时后自动停止并回调
        handler.postDelayed(() -> {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (Exception ignored) {}
            if (listener != null) {
                List<DiscoveredDevice> result;
                synchronized (foundDevices) {
                    result = new ArrayList<>(foundDevices);
                }
                listener.onDevicesFound(result);
            }
        }, timeoutMs);

        return discoveryListener;
    }
}