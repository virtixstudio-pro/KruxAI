package com.virtixstudio.kruxai.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

/** Dit à l'app quand Internet part et revient. Les rappels arrivent toujours sur le thread principal. */
public final class NetworkWatcher {

    public interface Listener { void onChanged(boolean online); }

    private final ConnectivityManager cm;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback callback;
    private volatile boolean online = true;

    public NetworkWatcher(Context ctx, Listener l) {
        this.cm = (ConnectivityManager) ctx.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        this.listener = l;
        this.online = check();
    }

    public boolean isOnline() { return online; }

    private boolean check() {
        try {
            if (cm == null) return true;
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities c = cm.getNetworkCapabilities(n);
            return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true; // en cas de doute, on laisse KRUX essayer
        }
    }

    private void set(final boolean now) {
        main.post(() -> {
            if (online == now) return;
            online = now;
            if (listener != null) listener.onChanged(now);
        });
    }

    public void start() {
        if (cm == null || callback != null) return;
        callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { set(true); }
            @Override public void onLost(Network network) { set(check()); }
            @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
                set(c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
            }
        };
        try { cm.registerDefaultNetworkCallback(callback); } catch (Exception e) { callback = null; }
        online = check();
    }

    public void stop() {
        if (cm != null && callback != null) {
            try { cm.unregisterNetworkCallback(callback); } catch (Exception ignore) { }
        }
        callback = null;
    }
}
