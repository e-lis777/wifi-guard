package org.wifimotion.test;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.security.SecureRandom;
import java.util.Base64;
public final class Store {
 public final SharedPreferences p;
 public Store(Context c){p=c.getSharedPreferences("guard",0);}
 public JSONObject config(){try{return new JSONObject(p.getString("config","{}"));}catch(Exception e){return new JSONObject();}}
 public boolean ready(){return config().has("key");}
 public boolean tv(){return p.getString("role","").equals("tv");}
 public static String random(int n){byte[] bytes=new byte[n];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 public void create(String name)throws Exception{JSONObject c=new JSONObject().put("v",1).put("name",name).put("server","https://ntfy.sh").put("commands","wg-c-"+random(24)).put("events","wg-e-"+random(24)).put("key",random(32));p.edit().putString("config",c.toString()).putString("role","tv").apply();}
 public void importPhone(String raw)throws Exception {JSONObject c=new JSONObject(raw);if(c.getInt("v")!=1||!c.getString("server").equals("https://ntfy.sh")||Base64.getUrlDecoder().decode(c.getString("key")).length!=32||!c.getString("commands").matches("wg-c-[A-Za-z0-9_-]{32}")||!c.getString("events").matches("wg-e-[A-Za-z0-9_-]{32}")||c.optString("name").length()>80)throw new Exception("Неверный код подключения");p.edit().clear().putString("config",c.toString()).putString("role","phone").apply();}
 public void log(String message){String line=new java.text.SimpleDateFormat("dd.MM HH:mm:ss",java.util.Locale.getDefault()).format(new java.util.Date())+"  "+message;String old=p.getString("journal","");String all=line+"\n"+old;if(all.length()>30000)all=all.substring(0,30000);p.edit().putString("journal",all).apply();}
}
