package org.wifimotion.test;
import android.app.*;
import android.content.*;
import android.net.*;
import android.net.wifi.*;
import android.os.*;
import okhttp3.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public class GuardService extends Service {
 Store store;JSONObject config;Handler h=new Handler();OkHttpClient http;WebSocket socket;boolean stopped=false,connected=false;
 final ExecutorService sender=Executors.newSingleThreadExecutor();Detector detector=new Detector();
 WifiManager wifi;WifiManager.WifiLock wifiKeepAlive;PowerManager.WakeLock wake;int rssi=-127,freq=0,phase=0,testCount=0;long phaseEnd=0,armAt=0,lastAlert=0,lastHeartbeat=0,lastProbe=0,lastNotification=0;
 ArrayList<Integer> bg=new ArrayList<>();boolean calibration=false,armed=false,validWifi=false;String note="",network="";double maxPass=0;int movementCount=0;
 final Runnable tick=new Runnable(){public void run(){try{step();}catch(Exception e){note="Ошибка измерения: "+e.getClass().getSimpleName();}if(!stopped)h.postDelayed(this,500);}};
 @Override public void onCreate(){super.onCreate();store=new Store(this);config=store.config();
  NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("service","Работа датчика и приём уведомлений",NotificationManager.IMPORTANCE_LOW));nm.createNotificationChannel(new NotificationChannel("alarm","Движение и потеря связи",NotificationManager.IMPORTANCE_HIGH));
  startForeground(1,notification("Wi-Fi Охрана","Запуск…","service"));
  http=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(0,TimeUnit.SECONDS).writeTimeout(15,TimeUnit.SECONDS).pingInterval(45,TimeUnit.SECONDS).build();
  wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);armed=store.p.getBoolean("armed",false);loadCalibration();
  PowerManager pm=getSystemService(PowerManager.class);wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"WifiGuard:monitor");wake.acquire();
  if(store.tv())try{wifiKeepAlive=wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"WifiGuard:radio");wifiKeepAlive.setReferenceCounted(false);wifiKeepAlive.acquire();store.log("Удержание Wi-Fi включено");}catch(Exception e){store.log("Не удалось удерживать Wi-Fi: "+e.getClass().getSimpleName());}
  if(store.ready()){connect();h.post(tick);}else stopSelf();
 }
 Notification notification(String title,String text,String channel){Intent i=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_lock_idle_lock).setContentTitle(title).setContentText(text).setContentIntent(pi).setAutoCancel(!channel.equals("service")).build();}
 void alarm(String text){getSystemService(NotificationManager.class).notify(2,notification(config.optString("name","Wi-Fi Охрана"),text,"alarm"));}
 void loadCalibration(){calibration=store.p.getBoolean("calibrated",false);detector.mean=Double.longBitsToDouble(store.p.getLong("mean",0));detector.sd=Double.longBitsToDouble(store.p.getLong("sd",0));detector.sensitivity=store.p.getInt("sensitivity",1);}
 @Override public int onStartCommand(Intent i,int flags,int id){if(i!=null){String action=i.getStringExtra("action");if(action!=null)try{local(action,i);}catch(Exception e){note="Не удалось выполнить действие";}}return START_STICKY;}
 void local(String action,Intent i)throws Exception{
  if(action.equals("calibrate")&&store.tv()){armed=false;armAt=0;store.p.edit().putBoolean("armed",false).putBoolean("calibrated",false).apply();calibration=false;phase=1;phaseEnd=System.currentTimeMillis()+30000;bg.clear();note="Отойди из зоны и не двигайся 30 секунд";detector.reset();publishStatus("");}
  else if(action.equals("passes")&&store.tv()&&phase==2){phase=3;phaseEnd=System.currentTimeMillis()+30000;maxPass=0;testCount=0;detector.reset();note="Пройди между роутером и ТВ три раза";}
  else if(action.equals("arm")||action.equals("disarm")||action.equals("status")){if(store.tv())command(action,"");else sendCommand(action);}
  else if(action.equals("sensitivity")&&store.tv()){detector.sensitivity=i.getIntExtra("value",1);detector.reset();store.p.edit().putInt("sensitivity",detector.sensitivity).apply();}
 }
 void command(String action,String request)throws Exception{
  if(action.equals("arm")){if(!calibration||!validWifi||phase!=0){note="Нельзя включить: нужна калибровка и доступный Wi-Fi";publishStatus(request);return;}if(!armed&&armAt==0){armAt=System.currentTimeMillis()+30000;note="30 секунд на выход";store.log("Начат отсчёт включения охраны");}publishStatus(request);}
  else if(action.equals("disarm")){armed=false;armAt=0;store.p.edit().putBoolean("armed",false).apply();note="Охрана выключена";store.log(note);publishStatus(request);}
  else if(action.equals("status"))publishStatus(request);
 }
 void sendCommand(String action)throws Exception{long now=System.currentTimeMillis();if(now-lastProbe<8000){note="Подожди несколько секунд перед новой командой";return;}lastProbe=now;String id=UUID.randomUUID().toString();store.p.edit().putString("pending",id).putLong("pendingAt",now).putString("pendingAction",action).apply();note="Ожидаем подтверждение от ТВ…";publish(config.getString("commands"),new JSONObject().put("kind","command").put("action",action).put("request",id),false);}
 void connect(){if(stopped)return;String topic=config.optString(store.tv()?"commands":"events");String url=config.optString("server").replace("https://","wss://")+"/"+topic+"/ws";if(!store.tv()){String cursor=store.p.getString("cursor","");url+="?since="+(cursor.isEmpty()?"12h":cursor);}
  socket=http.newWebSocket(new Request.Builder().url(url).build(),new WebSocketListener(){
   public void onOpen(WebSocket ws,Response response){h.post(()->{connected=true;note="Соединение с доставкой установлено";if(store.tv())try{publishStatus("");}catch(Exception ignored){};});}
   public void onMessage(WebSocket ws,String text){if(text.length()>7000)return;try{JSONObject outer=new JSONObject(text);if(!outer.optString("event").equals("message"))return;JSONObject message=new SecureMessage(config.getString("key"),topic).open(outer.getString("message"));h.post(()->receive(message,outer.optString("id")));}catch(Exception ignored){}}
   public void onFailure(WebSocket ws,Throwable e,Response response){lost();}
   public void onClosed(WebSocket ws,int code,String reason){lost();}
   public void onClosing(WebSocket ws,int code,String reason){ws.close(code,null);}
   void lost(){h.post(()->{connected=false;if(!stopped){note="Доставка недоступна; повтор подключения через минуту";h.postDelayed(()->connect(),60000);}});}
  });
 }
 void receive(JSONObject m,String cursor){try{
  long now=System.currentTimeMillis();if(!SecureMessage.timely(m,now,store.tv()?90000:12*3600000L))return;
  String id=m.getString("id");JSONArray seen=new JSONArray(store.p.getString("seen","[]"));for(int j=0;j<seen.length();j++)if(id.equals(seen.getString(j)))return;
  JSONArray next=new JSONArray();for(int j=Math.max(0,seen.length()-127);j<seen.length();j++)next.put(seen.getString(j));next.put(id);store.p.edit().putString("seen",next.toString()).apply();
  if(store.tv()){if(!m.optString("kind").equals("command"))return;String action=m.optString("action");if(!Arrays.asList("arm","disarm","status").contains(action))return;command(action,m.getString("request"));}
  else {store.p.edit().putString("cursor",cursor).apply();String kind=m.optString("kind");long time=m.getLong("time");
   if(time>store.p.getLong("lastSeen",0))store.p.edit().putLong("lastSeen",time).putBoolean("lostAlert",false).apply();
   if(kind.equals("status")){if(time>=store.p.getLong("statusTime",0)){String room=m.optString("name","");if(!room.isEmpty()&&room.length()<=80){config.put("name",room);store.p.edit().putString("config",config.toString()).apply();}store.p.edit().putString("remote",m.toString()).putLong("statusTime",time).apply();}if(m.optString("request").equals(store.p.getString("pending",""))&&!m.optString("request").isEmpty()){store.p.edit().remove("pending").remove("pendingAt").apply();note="Состояние подтверждено ТВ";}}
   else if(kind.equals("motion")){store.log("Движение: "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(time)));if(now-time<120000)alarm("Обнаружено движение");}
  }
 }catch(Exception ignored){}}
 void publishStatus(String request)throws Exception{JSONObject m=new JSONObject().put("kind","status").put("name",config.optString("name","Датчик")).put("request",request).put("armed",armed).put("exitUntil",armAt).put("calibrated",calibration).put("wifi",validWifi).put("rssi",rssi).put("frequency",freq).put("note",note).put("count",movementCount);publish(config.getString("events"),m,true);lastHeartbeat=System.currentTimeMillis();}
 void publish(String topic,JSONObject m,boolean cache)throws Exception{long now=System.currentTimeMillis();String day=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(now));int used=day.equals(store.p.getString("budgetDay",""))?store.p.getInt("budget",0):0;
  if(used>=220){note="Локальный лимит отправок на сегодня исчерпан. Журнал работает.";store.log(note);return;}m.put("id",UUID.randomUUID().toString()).put("time",now);String encrypted=new SecureMessage(config.getString("key"),topic).seal(m);store.p.edit().putString("budgetDay",day).putInt("budget",used+1).apply();
  sender.execute(()->{try(Response response=http.newBuilder().readTimeout(20,TimeUnit.SECONDS).build().newCall(new Request.Builder().url(config.optString("server")+"/"+topic).header("X-Firebase","no").header("X-Cache",cache?"yes":"no").post(RequestBody.create(MediaType.parse("text/plain"),encrypted)).build()).execute()){if(!response.isSuccessful())h.post(()->{note=response.code()==429?"Сервис ограничил отправки. Журнал сохраняется.":"Ошибка доставки HTTP "+response.code();store.log(note);});}catch(Exception e){h.post(()->note="Сообщение не доставлено; проверь интернет");}});
 }
 void step()throws Exception{long now=System.currentTimeMillis();
  if(store.tv()){
   ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());WifiInfo info=wifi.getConnectionInfo();rssi=info.getRssi();freq=info.getFrequency();network=info.getNetworkId()+"|"+freq+"|"+info.getBSSID();boolean prior=validWifi;validWifi=caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&rssi>-127&&rssi<0&&freq>0;
   if(!validWifi){detector.reset();if(phase!=0){phase=0;calibration=false;store.p.edit().putBoolean("calibrated",false).apply();note="Калибровка прервана: нет измерений Wi-Fi";}}
   else{
    if(calibration&&!network.equals(store.p.getString("network",""))){calibration=false;store.p.edit().putBoolean("calibrated",false).apply();armAt=0;note="Сеть или канал изменились. Повтори калибровку";publishStatus("");}
    if(phase==1){bg.add(rssi);if(now>=phaseEnd){if(bg.size()<45){phase=0;note="Мало измерений. Повтори калибровку";}else{detector.mean=0;for(int x:bg)detector.mean+=x;detector.mean/=bg.size();double v=0;for(int x:bg)v+=(x-detector.mean)*(x-detector.mean);detector.sd=Math.sqrt(v/bg.size());phase=2;note="Фон измерен. Нажми «Проверить проходы»";store.p.edit().putString("calNetwork",network).apply();}}}
    else if(phase==3){maxPass=Math.max(maxPass,Math.abs(rssi-detector.mean));if(detector.sample(rssi))testCount++;if(now>=phaseEnd){phase=0;calibration=testCount>=2&&maxPass>=Math.max(3,detector.sd*3+1)&&network.equals(store.p.getString("calNetwork",""));store.p.edit().putBoolean("calibrated",calibration).putLong("mean",Double.doubleToLongBits(detector.mean)).putLong("sd",Double.doubleToLongBits(detector.sd)).putString("network",network).apply();note=calibration?"Можно включать охрану. Проходов обнаружено: "+testCount:"Нужно повторить: проходы плохо отличаются от фона (событий: "+testCount+")";store.log(note);publishStatus("");detector.reset();}}
    else if(phase==0){if(armAt>0&&now>=armAt){armAt=0;armed=true;detector.reset();store.p.edit().putBoolean("armed",true).apply();note="Охрана включена";store.log(note);publishStatus("");}if(armed&&calibration&&detector.sample(rssi)){movementCount++;store.log("Движение • "+rssi+" dBm");if(now-lastAlert>=60000){lastAlert=now;publish(config.getString("events"),new JSONObject().put("kind","motion").put("rssi",rssi).put("count",movementCount),true);}}}
   }
   if(prior!=validWifi&&connected)publishStatus("");if(connected&&now-lastHeartbeat>=1800000)publishStatus("");
   store.p.edit().putInt("rssi",rssi).putInt("freq",freq).putBoolean("wifi",validWifi).putBoolean("connected",connected).putInt("phase",phase).putLong("phaseEnd",phaseEnd).putLong("armAt",armAt).putString("note",note).putInt("movementCount",movementCount).apply();
  }else{
   long pending=store.p.getLong("pendingAt",0);if(pending>0&&now-pending>45000){store.p.edit().remove("pending").remove("pendingAt").apply();note="Нет подтверждения команды. Обнови состояние; режим мог измениться.";}
   long seen=store.p.getLong("lastSeen",0);if(seen>0&&now-seen>35*60000L&&!store.p.getBoolean("lostAlert",false)){store.p.edit().putBoolean("lostAlert",true).apply();alarm("Давно нет подтверждения от датчика. Проверь связь.");store.log("Нет свежего состояния датчика");}
   store.p.edit().putBoolean("connected",connected).putString("note",note).apply();
  }
  if(now-lastNotification>5000){lastNotification=now;getSystemService(NotificationManager.class).notify(1,notification("Wi-Fi Охрана",store.tv()?(!validWifi?"Нет измерений Wi-Fi":armed?(calibration?"Охрана включена":"Охрана приостановлена: калибровка"):armAt>0?"Отсчёт включения":"Охрана выключена"):(connected?"Приём уведомлений работает":"Переподключение…"),"service"));}
 }
 @Override public void onDestroy(){stopped=true;h.removeCallbacksAndMessages(null);if(socket!=null)socket.cancel();sender.shutdownNow();if(wifiKeepAlive!=null&&wifiKeepAlive.isHeld())wifiKeepAlive.release();if(wake!=null&&wake.isHeld())wake.release();store.p.edit().putBoolean("connected",false).apply();super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
