package org.wifimotion.test;
import java.util.Locale;
public final class LinkGuard {
 public static final int UNKNOWN=0,SAME=1,DIFFERENT=2;
 String candidate="";int count=0;
 static String[] parts(String value){return value.split("\\|",-1);}
 static int number(String value){try{return Integer.parseInt(value);}catch(Exception e){return -1;}}
 static int band(int frequency){return frequency<=0?0:frequency<3000?2:frequency<5925?5:6;}
 static boolean known(String bssid){return bssid.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")&&!bssid.equalsIgnoreCase("02:00:00:00:00:00")&&!bssid.equalsIgnoreCase("00:00:00:00:00:00");}
 public static int compare(String saved,String current){String[] a=parts(saved),b=parts(current);if(a.length!=3||b.length!=3)return UNKNOWN;int af=band(number(a[1])),bf=band(number(b[1]));if(af==0||bf==0)return UNKNOWN;if(af!=bf)return DIFFERENT;if(known(a[2])&&known(b[2]))return a[2].equalsIgnoreCase(b[2])?SAME:DIFFERENT;int ai=number(a[0]),bi=number(b[0]);return ai>=0&&ai==bi?SAME:UNKNOWN;}
 public boolean changed(String saved,String current){if(compare(saved,current)!=DIFFERENT){reset();return false;}String[] p=parts(current);String identity=band(number(p[1]))+"|"+(known(p[2])?p[2].toLowerCase(Locale.US):"unknown");if(identity.equals(candidate))count++;else{candidate=identity;count=1;}return count>=12;}
 public void reset(){candidate="";count=0;}
}
