/*
 * Copyright (c) 2015 OpenSilk Productions LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package syncthing.android.service;

import android.annotation.TargetApi;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.RemoteException;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.opensilk.common.core.dagger2.ForApplication;
import org.opensilk.common.core.util.BundleHelper;
import org.opensilk.common.core.util.VersionUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import timber.log.Timber;

/**
 * Created by drew on 3/21/15.
 */
public class ServiceSettings {

    public static final String ENABLED = "local_instance_enabled";
    public static final String INITIALISED = "local_instance_initialised";

    public static final String RUN_WHEN = "run_when";
    public static final String ALWAYS = "always";

    public static final String SCHEDULED = "scheduled";
    public static final String RANGE_START = "scheduled_start";
    public static final String RANGE_END = "scheduled_end";

    public static final String ONLY_WIFI = "only_on_wifi";
    public static final String WIFI_NETWORKS = "TRANSIENT_wifi_networks";
    public static final String ONLY_CHARGING = "only_when_charging";

    private final Context appContext;
    private final ConnectivityManager cm;
    private final WifiManager wm;
    private final Uri callUri;
    private final ReceiverHelper receiverHelper;

    private final Object clientLock = new Object();

    private ContentProviderClient client;
    private boolean cached;

    @Inject
    public ServiceSettings(
            @ForApplication Context appContext,
            ConnectivityManager cm,
            WifiManager wm,
            @Named("settingsAuthority") String authority,
            ReceiverHelper receiverHelper
    ) {
        this.appContext = appContext;
        this.cm = cm;
        this.wm = wm;
        this.callUri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT).authority(authority).build();
        this.receiverHelper = receiverHelper;
    }

    private Bundle getCall(String pref, Bundle extras) {
        return makeCall("get_settings", pref, extras, true);
    }

    private Bundle putCall(String pref, Bundle extras) {
        return makeCall("put_settings", pref, extras, true);
    }

    private Bundle makeCall(String method, String pref, Bundle extras, boolean retry) {
        if (cached && VersionUtils.hasApi17()) {
            try {
                return makeCallApi17(method, pref, extras);
            } catch (RemoteException e) {
                release();
                if (retry) {
                    return makeCall(method, pref, extras, false);
                } else {
                    return extras;//return defaults
                }
            }
        } else {
            return appContext.getContentResolver().call(callUri, method, pref, extras);
        }
    }

    @TargetApi(17)
    private Bundle makeCallApi17(String method, String pref, Bundle extras) throws RemoteException {
        synchronized (clientLock) {
            if (client == null) {
                //clients dramatically improve performance and is what
                //content resolver does under the hood anyway.
                client = appContext.getContentResolver()
                        .acquireUnstableContentProviderClient(callUri);
                if (client == null) {
                    throw new RuntimeException("Unable to connect to our *own* content provider !!!!");
                }
            }
            return client.call(method, pref, extras);
        }
    }

    public void release() {
        synchronized (clientLock) {
            if (client != null) {
                client.release();
                client = null;
            }
        }
    }

    public void setCached(boolean cached) {
        this.cached = cached;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (client != null) {
            Timber.e("Settings were not released!!!");
            release();
        }
    }

    public boolean isDisabled() {
        return !isEnabled();
    }

    public boolean isEnabled() {
        Bundle reply = getCall(ENABLED, BundleHelper.b().putInt(0).get());
        return BundleHelper.getInt(reply) == 1;
    }

    public void setEnabled(boolean enabled) {
        Bundle reply = putCall(ENABLED, BundleHelper.b().putInt(enabled ? 1 : 0).get());
        if (StringUtils.equals(BundleHelper.getString(reply), "ok")) {
            receiverHelper.setBootReceiverEnabled(enabled);
            if (enabled) {
                receiverHelper.setConnectivityReceiverEnabled(onlyOnWifi());
                receiverHelper.setChargingReceiverEnabled(onlyWhenCharging());
            } else {
                receiverHelper.setConnectivityReceiverEnabled(false);
                receiverHelper.setChargingReceiverEnabled(false);
            }
        }
    }

    public boolean isInitialised() {
        Bundle reply = getCall(INITIALISED, BundleHelper.b().putInt(0).get());
        return BundleHelper.getInt(reply) == 1;
    }

    public void setInitialized(boolean initialized) {
        Bundle reply = putCall(INITIALISED, BundleHelper.b().putInt(initialized ? 1 : 0).get());
        if (initialized && "ok".equals(BundleHelper.getString(reply))) {
            appContext.getContentResolver().notifyChange(getInitializedUri(), null);
        }
    }

    public String runWhen() {
        Bundle reply = getCall(RUN_WHEN, BundleHelper.b().putString(ALWAYS).get());
        return BundleHelper.getString(reply);
    }

    public void setRunWhen(String runWhen) {
        Bundle reply = putCall(RUN_WHEN, BundleHelper.b().putString(runWhen).get());
    }

    public boolean onlyWhenCharging() {
        Bundle reply = getCall(ONLY_CHARGING, BundleHelper.b().putInt(0).get());
        return BundleHelper.getInt(reply) == 1;
    }

    public void setOnlyWhenCharging(boolean onlyWhenCharging) {
        Bundle reply = putCall(ONLY_CHARGING, BundleHelper.b().putInt(onlyWhenCharging ? 1 : 0).get());
        if (StringUtils.equals(BundleHelper.getString(reply), "ok")) {
            receiverHelper.setChargingReceiverEnabled(onlyWhenCharging);
        }
    }

    public String getScheduledStartTime() {
        Bundle reply = getCall(RANGE_START, BundleHelper.b().putString("00:00").get());
        return BundleHelper.getString(reply);
    }

    public void setScheduledStartTime(String time) {
        Bundle reply = putCall(RANGE_START, BundleHelper.b().putString(time).get());
    }

    public String getScheduledEndTime() {
        Bundle reply = getCall(RANGE_END, BundleHelper.b().putString("00:00").get());
        return BundleHelper.getString(reply);
    }

    public void setScheduledEndTime(String time) {
        Bundle reply = putCall(RANGE_END, BundleHelper.b().putString(time).get());
    }

    public boolean onlyOnWifi() {
        Bundle reply = getCall(ONLY_WIFI, BundleHelper.b().putInt(0).get());
        return BundleHelper.getInt(reply) == 1;
    }

    public void setOnlyOnWifi(boolean onlyOnWifi) {
        Bundle reply = putCall(ONLY_WIFI, BundleHelper.b().putInt(onlyOnWifi ? 1 : 0).get());
        if (StringUtils.equals(BundleHelper.getString(reply), "ok")) {
            receiverHelper.setConnectivityReceiverEnabled(onlyOnWifi);
        }
    }

    public Set<String> allowedWifiNetworks() {
        Bundle reply = getCall(WIFI_NETWORKS, null);
        if (reply != null) {
            return unrollWifiNetworks(BundleHelper.getString(reply));
        } else {
            return Collections.emptySet();
        }
    }

    public void setAllowedWifiNetworks(Set<String> networks) {
        Bundle reply = putCall(WIFI_NETWORKS, BundleHelper.b().putString(rollWifiNetworks(networks)).get());
    }

    public Uri getInitializedUri() {
        return callUri.buildUpon().appendPath("instanceInitialized").build();
    }

    boolean isAllowedToRun() {
        if (isDisabled()) {
            Timber.d("isAllowedToRun(): SyncthingInstance disabled");
            return false;
        }
        if (!isInitialised()) {
            Timber.d("isAllowedToRun(): SyncthingInstance initiating credentials");
            return true;
        }
        if (onlyWhenCharging() && !isCharging()) {
            Timber.d("isAllowedToRun(): chargingOnly=true and not charging... rejecting");
            return false;
        }
        if (!hasSuitableConnection()) {
            Timber.d("isAllowedToRun(): No suitable network... rejecting");
            return false;
        }
        switch (runWhen()) {
            case SCHEDULED:
                long start = SyncthingUtils.parseTime(getScheduledStartTime());
                long end = SyncthingUtils.parseTime(getScheduledEndTime());
                boolean can = SyncthingUtils.isNowBetweenRange(start, end);
                Timber.d("isAllowedToRun(): is now a good time? %s", can);
                return can;
            case ALWAYS:
            default:
                Timber.d("isAllowedToRun(): Always mate!");
                return true;
        }
    }

    boolean isCharging() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent battChanged = appContext.registerReceiver(null, filter);//stickies returned by register
        int status = battChanged != null ? battChanged.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) : 0;
        return status != 0;
    }

    boolean hasSuitableConnection() {
        NetworkInfo info = cm.getActiveNetworkInfo();
        if (onlyOnWifi()) {
            if (info != null && info.isConnectedOrConnecting()) {
                if (!isWifiOrEthernet(info.getType())) {
                    Timber.d("Connection is not wifi network");
                    return false;
                } else if (isWifi(info.getType()) && !isConnectedToWhitelistedNetwork()) {
                    Timber.d("Connected wifi network not in whitelist");
                    return false;
                } else {
                    Timber.d("Connected to wifi or ethernet");
                    return true;
                }
            } else {
                Timber.d("No wifi or ethernet connection");
                return false;
            }
        } else if (info != null && info.isConnectedOrConnecting()) {
            Timber.d("Connected to network");
            return true;
        } else {
            Timber.d("Not connected to any networks... running anyway");
            //TODO add setting to only run when connected
            return true;
        }
    }

    static boolean isWifi(int type) {
        return type == ConnectivityManager.TYPE_WIFI;
    }

    static boolean isWifiOrEthernet(int type) {
        return isWifi(type) || type == ConnectivityManager.TYPE_ETHERNET;
    }

    boolean isConnectedToWhitelistedNetwork() {
        Set<String> whitelist = allowedWifiNetworks();
        if (whitelist == null || whitelist.isEmpty()) {
            Timber.d("No whitelist found");
            return true;
        }
        WifiInfo info =  wm.getConnectionInfo();
        if (info == null) {
            Timber.w("WifiInfo was null");
            return true;
        }
        String ssid = info.getSSID();
        if (ssid == null) {
            Timber.w("SSID was null");
            return true;
        }
        if (whitelist.contains(ssid)) {
            Timber.d("Found %s in whitelist", ssid);
            return true;
        } else {
            return false;
        }
    }

    boolean isOnSchedule() {
        return SCHEDULED.equals(runWhen());
    }

    long getNextScheduledEndTime() {
        long start = SyncthingUtils.parseTime(getScheduledStartTime());
        long end = SyncthingUtils.parseTime(getScheduledEndTime());
        DateTime now = DateTime.now();
        Interval interval = SyncthingUtils.getIntervalForRange(now, start, end);
        if (interval.contains(now)) {
            //With scheduled range
            return interval.getEndMillis();
        } else {
            //Outside scheduled range, shutdown asap
            return now.getMillis() + AlarmManagerHelper.KEEP_ALIVE;
        }
    }

    long getNextScheduledStartTime() {
        long start = SyncthingUtils.parseTime(getScheduledStartTime());
        long end = SyncthingUtils.parseTime(getScheduledEndTime());
        return getNextNextStartTimeFor(start, end);
    }

    static long getNextNextStartTimeFor(long start, long end) {
        DateTime now = DateTime.now();
        Interval interval = SyncthingUtils.getIntervalForRange(now, start, end);
        if (interval.isAfter(now)) {
            //Interval hasnt started yet
            return interval.getStartMillis();
        } else {
            //were either inside the interval or past it, get the next days start
            //XXX we count inside interval as next day so if user
            //    explicitly shuts us down we dont just start again
            return interval.getStart().plusDays(1).getMillis();
        }
    }

    static String rollWifiNetworks(Set<String> networks) {
        if (networks == null || networks.isEmpty()) {
            return "";
        }
        return StringUtils.join(networks, sep);
    }

    static Set<String> unrollWifiNetworks(String networks) {
        Set<String> set = new HashSet<>();
        String[] n = StringUtils.split(networks, sep);
        if (n != null && n.length > 0) {
            Collections.addAll(set, n);
        }
        return set;
    }

    private static final char sep = '★';//commas are boring

}
