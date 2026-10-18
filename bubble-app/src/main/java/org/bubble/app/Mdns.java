package org.bubble.app;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Finds the phone's own adb mDNS endpoints: {@code _adb-tls-connect._tcp} for a paired connection,
 * {@code _adb-tls-pairing._tcp} while the system "Pair device with pairing code" dialog is open.
 * The daemon advertises these through {@link NsdManager}, so an app on the same device discovers
 * them without reading any system UI — the user only reads the 6-digit code.
 */
final class Mdns {

    static final String PAIRING = "_adb-tls-pairing._tcp";
    static final String CONNECT = "_adb-tls-connect._tcp";

    private Mdns() {}

    /** Resolve the first endpoint of {@code serviceType}; null on timeout. Blocks. */
    static InetSocketAddress resolve(Context context, String serviceType, long timeoutMs)
            throws InterruptedException {
        NsdManager nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) return null;
        BlockingQueue<NsdServiceInfo> resolved = new ArrayBlockingQueue<>(1);

        NsdManager.ResolveListener resolveListener = new NsdManager.ResolveListener() {
            @Override public void onServiceResolved(NsdServiceInfo info) { resolved.offer(info); }
            @Override public void onResolveFailed(NsdServiceInfo info, int errorCode) {}
        };
        NsdManager.DiscoveryListener discoveryListener = new NsdManager.DiscoveryListener() {
            @Override public void onServiceFound(NsdServiceInfo info) {
                if (serviceType.equals(normalize(info.getServiceType()))) {
                    try {
                        nsd.resolveService(info, resolveListener);
                    } catch (IllegalArgumentException alreadyActive) { /* one resolve at a time */ }
                }
            }
            @Override public void onServiceLost(NsdServiceInfo info) {}
            @Override public void onDiscoveryStarted(String type) {}
            @Override public void onDiscoveryStopped(String type) {}
            @Override public void onStartDiscoveryFailed(String type, int errorCode) {}
            @Override public void onStopDiscoveryFailed(String type, int errorCode) {}
        };

        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        try {
            NsdServiceInfo info = resolved.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (info == null) return null;
            InetAddress host = info.getHost();
            return host == null ? null : new InetSocketAddress(host, info.getPort());
        } finally {
            try {
                nsd.stopServiceDiscovery(discoveryListener);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /** mDNS service types arrive with a trailing dot and sometimes a leading one. */
    private static String normalize(String serviceType) {
        if (serviceType == null) return null;
        String s = serviceType.endsWith(".") ? serviceType.substring(0, serviceType.length() - 1) : serviceType;
        return s.startsWith(".") ? s.substring(1) : s;
    }
}
