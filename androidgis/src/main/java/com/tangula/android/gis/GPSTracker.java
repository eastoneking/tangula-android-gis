package com.tangula.android.gis;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.util.Consumer;

import com.tangula.android.utils.ApplicationUtils;
import com.tangula.android.utils.LogUt;
import com.tangula.android.utils.TaskUtils;

import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("unused")
public class GPSTracker {

    @SuppressWarnings("WeakerAccess")
    public interface ProviderStatusChangedListener {
        void onStatusChanged(String provider, int status, Bundle extras);
    }

    private static class CallbackFuncSet {
        Consumer<Location> onLocationChanged;
        Runnable onGpsNetwordBothDisable;
        Runnable onNotGrantPermission;
        Runnable onNoLocationManager;
        Consumer<String> onDisableLocationProvider;
        Consumer<String> onEnableLocationProvider;
        ProviderStatusChangedListener onProviderStatusChanged;

        CallbackFuncSet(Consumer<Location> onLocationChanged,
                        Runnable onGpsNetwordBothDisable,
                        Runnable onNotGrantPermission,
                        Runnable onNoLocationManager,
                        Consumer<String> onDisableLocationProvider,
                        Consumer<String> onEnableLocationProvider,
                        ProviderStatusChangedListener onProviderStatusChanged) {
            this.onLocationChanged = onLocationChanged;
            this.onGpsNetwordBothDisable = onGpsNetwordBothDisable;
            this.onNotGrantPermission = onNotGrantPermission;
            this.onNoLocationManager = onNoLocationManager;
            this.onDisableLocationProvider = onDisableLocationProvider;
            this.onEnableLocationProvider = onEnableLocationProvider;
            this.onProviderStatusChanged = onProviderStatusChanged;
        }

    }


    // The minimum distance to change Updates in meters
    private static long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10; // 10 meters

    // The minimum time between updates in milliseconds
    private static long MIN_TIME_BW_UPDATES = 60000; // 1 minute

    private static final List<CallbackFuncSet> CALLBACKS = new LinkedList<>();

    private static final LocationListener LISTENER = new LocationListener() {
        @Override
        public void onLocationChanged(final Location location) {
            if (location == null) {
                return;
            }
            TaskUtils.runInAsyncPool(new Runnable() {
                @Override
                public void run() {

                    for (CallbackFuncSet cur : CALLBACKS) {
                        try {
                            if (cur != null && cur.onLocationChanged != null) {
                                cur.onLocationChanged.accept(location);
                            }
                        } catch (Throwable e) {
                            LogUt.e(e);
                        }
                    }
                }
            });
        }

        @Override
        public void onStatusChanged(final String provider, final int status, final Bundle extras) {
            TaskUtils.runInAsyncPool(new Runnable() {
                @Override
                public void run() {
                    for (CallbackFuncSet cur : CALLBACKS) {
                        try {
                            if (cur != null && cur.onProviderStatusChanged != null) {
                                cur.onProviderStatusChanged.onStatusChanged(provider, status, extras);
                            }
                        } catch (Throwable e) {
                            LogUt.e(e);
                        }
                    }
                }
            });
        }

        @Override
        public void onProviderEnabled(final String provider) {
            TaskUtils.runInAsyncPool(new Runnable() {
                @Override
                public void run() {
                    for (CallbackFuncSet cur : CALLBACKS) {
                        try {
                            if (cur != null && cur.onEnableLocationProvider != null) {
                                cur.onEnableLocationProvider.accept(provider);
                            }
                        } catch (Throwable e) {
                            LogUt.e(e);
                        }
                    }
                }
            });
        }

        @Override
        public void onProviderDisabled(final String provider) {
            TaskUtils.runInAsyncPool(new Runnable() {
                @Override
                public void run() {
                    for (CallbackFuncSet cur : CALLBACKS) {
                        try {
                            if (cur != null && cur.onDisableLocationProvider != null) {
                                cur.onDisableLocationProvider.accept(provider);
                            }
                        } catch (Throwable e) {
                            LogUt.e(e);
                        }
                    }
                }
            });
        }

    };

    public static void unwatchLocationChanged(Consumer<Location> onLocationChanged) {
        CallbackFuncSet found = null;
        for (CallbackFuncSet cur : CALLBACKS) {
            if (cur != null && cur.onLocationChanged == onLocationChanged) {
                found = cur;
                break;
            }
        }

        if (found != null) {
            CALLBACKS.remove(found);
        }
    }

    public static void watchLocationChanged(Consumer<Location> onLocationChanged) {
        watchLocationChanged(ApplicationUtils.APP, onLocationChanged,
                null,null,
                null,null,
                null,null);
    }


    public static void watchLocationChanged(Context mContext,
                                            Consumer<Location> onLocationChanged,
                                            Runnable onGpsNetwordBothDisable,
                                            Runnable onNotGrantPermission,
                                            Runnable onNoLocationManager,
                                            Consumer<String> onDisablelLocationProvider,
                                            Consumer<String> onEnableLocationProvider,
                                            ProviderStatusChangedListener onProviderStatusChanged
    ) {

        stopGps(mContext);

        // flag for GPS status
        boolean isGPSEnabled = false;

        // flag for network status
        boolean isNetworkEnabled = false;

        Context ctx = (mContext == null ? ApplicationUtils.APP : mContext);

        try {
            final LocationManager locationManager = (LocationManager)
                    ctx.getSystemService(Context.LOCATION_SERVICE);

            if (locationManager == null) {
                try {
                    if (onNoLocationManager != null) {
                        onNoLocationManager.run();
                    }
                } catch (Throwable t) {
                    //ignore
                }
            }

            if (onLocationChanged != null) {
                CALLBACKS.add(new CallbackFuncSet(onLocationChanged, onGpsNetwordBothDisable,
                        onNotGrantPermission, onNoLocationManager, onDisablelLocationProvider,
                        onEnableLocationProvider, onProviderStatusChanged));
            }

            if (locationManager != null) {
                // getting GPS status
                isGPSEnabled = locationManager
                        .isProviderEnabled(LocationManager.GPS_PROVIDER);
                // getting network status
                isNetworkEnabled = locationManager
                        .isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            }
            if (!isGPSEnabled && !isNetworkEnabled) {
                if (onGpsNetwordBothDisable != null) {
                    try {
                        onGpsNetwordBothDisable.run();
                    } catch (Throwable t) {
                        //ignore
                    }
                }
            } else {
                // First get location from Network Provider
                if (isNetworkEnabled) {
                    PermissionCheck.onHaveGpsPerms(ctx, new Runnable() {
                        @SuppressLint("MissingPermission")
                        @Override
                        public void run() {
                            locationManager.requestLocationUpdates(
                                    LocationManager.NETWORK_PROVIDER,
                                    MIN_TIME_BW_UPDATES,
                                    MIN_DISTANCE_CHANGE_FOR_UPDATES, LISTENER);
                        }
                    }, onNotGrantPermission);
                }
                // if GPS Enabled get lat/long using GPS Services
                else {
                    PermissionCheck.onHaveGpsPerms(mContext, new Runnable() {
                        @SuppressLint("MissingPermission")
                        @Override
                        public void run() {
                            locationManager.requestLocationUpdates(
                                    LocationManager.GPS_PROVIDER,
                                    MIN_TIME_BW_UPDATES,
                                    MIN_DISTANCE_CHANGE_FOR_UPDATES, LISTENER);
                        }
                    }, onNotGrantPermission);
                }
            }

        } catch (Exception e) {
            LogUt.e(e);
        }
    }

    /**
     * Stop using GPS listener
     * Calling this function will stop using GPS in your app
     */
    @SuppressWarnings("unused")
    public static void stopGps(Context ctx) {
        try {
            final Context mContext = ctx == null ? ApplicationUtils.APP : ctx;
            final LocationManager locationManager = (LocationManager) (mContext == null ? ApplicationUtils.APP : mContext)
                    .getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {
                PermissionCheck.onHaveGpsPerms(ctx, new Runnable() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void run() {
                        locationManager.removeUpdates(LISTENER);
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                    }
                });
            }
        }catch(Exception e){
            LogUt.e(e);
        }
    }


    /**
     * Function to show settings alert dialog
     * On pressing Settings button will lauch Settings Options
     */
    @SuppressWarnings("unused")
    public static void showGpsSettingsAlert(Context ctx, String title, String msg, String settingsBtnText, String cancalBtnText) {
        final Context mContext = ctx == null ? ApplicationUtils.APP : ctx;
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(mContext);

        // Setting Dialog Title
        alertDialog.setTitle(title);

        // Setting Dialog Message
        alertDialog.setMessage(msg);

        // On pressing Settings button
        alertDialog.setPositiveButton(settingsBtnText, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                mContext.startActivity(intent);
            }
        });

        // on pressing cancel button
        alertDialog.setNegativeButton(cancalBtnText, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        // Showing Alert Message
        alertDialog.show();
    }

}