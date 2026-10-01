package org.wifimotion.test;
import android.app.Application;
public class GuardApp extends Application {
 public void onCreate(){super.onCreate();Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();Thread.setDefaultUncaughtExceptionHandler((thread,error)->{record(error);if(previous!=null)previous.uncaughtException(thread,error);else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(1);}});}
 public void record(Throwable error){try{StringBuilder out=new StringBuilder();out.append("Wi-Fi Guard 0.2.1 / Android ").append(android.os.Build.VERSION.RELEASE).append(" / ").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL).append('\n');Throwable cause=error;for(int n=0;cause!=null&&n<4;n++,cause=cause.getCause()){out.append(cause.getClass().getName()).append('\n');StackTraceElement[] frames=cause.getStackTrace();for(int i=0;i<Math.min(18,frames.length);i++)out.append("  ").append(frames[i]).append('\n');}getSharedPreferences("diagnostics",0).edit().putString("crash",out.toString()).commit();}catch(Exception ignored){}}
}
