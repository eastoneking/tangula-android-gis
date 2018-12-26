package com.tangula.android.gis;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

import com.tangula.android.utils.LogUt;

import java.util.ArrayList;
import java.util.List;

class PermissionCheck {


    static void onHaveGpsPerms(final Context context, final Runnable onHavePerm, final Runnable onNoPerm){
        checkPerms(context, onHavePerm, onNoPerm, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION);
    }



    private static  void checkPerms(Context context, final Runnable onSucces, final Runnable onNotGrant, String... permissionList){
         checkPerms(context, permissionList, onSucces, onNotGrant);
    }

    private static  void checkPerms(Context context, String[] permissionList, final Runnable onSucces, final Runnable onNotGrant){
        if(permissionList!=null){
            List<String> noPerms = new ArrayList<>();
            for(String perm: permissionList){
                try {
                    if (context.checkPermission(perm, Process.myPid(), Process.myUid()) != PackageManager.PERMISSION_GRANTED) {
                        noPerms.add(perm);
                    }
                }catch (Exception e){
                    //ignore
                }
            }

            if(noPerms.isEmpty()){
                try {
                    onSucces.run();
                }catch(Throwable t){
                    LogUt.e(t);
                }
            }else{
                if(onNotGrant!=null){
                    try {
                        onNotGrant.run();
                    }catch (Throwable t){
                        //ignore
                    }
                }
            }
        }
    }
}
