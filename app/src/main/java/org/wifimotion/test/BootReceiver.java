package org.wifimotion.test;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {public void onReceive(Context c,Intent i){if(new Store(c).ready())c.startForegroundService(new Intent(c,GuardService.class));}}
