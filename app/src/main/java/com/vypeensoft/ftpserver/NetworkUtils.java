package com.vypeensoft.ftpserver;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Locale;

public class NetworkUtils {
    private static final String TAG = "NetworkUtils";

    public static String getWifiIpAddress(Context context) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try {
            if (wm != null && wm.isWifiEnabled()) {
                int ipAddress = wm.getConnectionInfo().getIpAddress();
                if (ipAddress != 0) {
                    String ip = String.format(Locale.US, "%d.%d.%d.%d",
                            (ipAddress & 0xff),
                            (ipAddress >> 8 & 0xff),
                            (ipAddress >> 16 & 0xff),
                            (ipAddress >> 24 & 0xff));
                    Log.d(TAG, "IP via WifiManager: " + ip);
                    return ip;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get IP from WifiManager, using NetworkInterface fallback: " + e.getMessage());
        }
        
        // Fallback or Hotspot Mode: search network interfaces
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        String ip = inetAddress.getHostAddress();
                        Log.d(TAG, "IP via NetworkInterfaces: " + ip);
                        return ip;
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(TAG, "Error getting network interfaces", ex);
        }
        return "127.0.0.1";
    }

    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }

    public static String generateFtpUrl(String ip, int port) {
        return "ftp://" + ip + ":" + port;
    }

    public static boolean isPortAvailable(int port) {
        if (port < 1024 || port > 65535) {
            return false;
        }
        ServerSocket ss = null;
        try {
            ss = new ServerSocket(port);
            ss.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            Log.d(TAG, "Port " + port + " is not available: " + e.getMessage());
        } finally {
            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        return false;
    }
}
