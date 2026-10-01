package org.wifimotion.test;
import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.ScanContract;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import java.util.*;

public class MainActivity extends Activity {
 Store store;LinearLayout root;TextView state,detail,journal;Button arm,passes;Handler handler=new Handler();Graph graph;
 final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,1000);}};
 @Override public void onCreate(Bundle b){super.onCreate(b);store=new Store(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);build();if(store.ready())start(null);}
 void build(){ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(30,20,30,20);scroll.addView(root);setContentView(scroll);label("Wi-Fi Охрана • 0.2.1",27); String diagnostic=getSharedPreferences("diagnostics",0).getString("crash","");if(!diagnostic.isEmpty())button("Последняя ошибка — показать",()->new AlertDialog.Builder(this).setTitle("Диагностика без ключей доступа").setMessage(diagnostic).setPositiveButton("Копировать",(d,w)->((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Wi-Fi Guard diagnostic",diagnostic))).setNegativeButton("Закрыть",null).show());
  if(!store.ready()){label("Один APK для ТВ и телефона. Обнаружение локальное; зашифрованные команды и события передаются через бесплатный ntfy.sh. Своего сервера нет. Приложение не является сертифицированной охранной системой.",17);
   button("Датчик на ТВ",()->{EditText name=new EditText(this);name.setSingleLine();name.setText("Гостиная");new AlertDialog.Builder(this).setTitle("Название датчика").setView(name).setPositiveButton("Создать",(d,w)->{try{String value=name.getText().toString().trim();if(value.isEmpty())value="Гостиная";store.create(value.substring(0,Math.min(80,value.length())));build();permissions(true);start(null);showQr();}catch(Exception e){message("Не удалось создать настройки");}}).setNegativeButton("Отмена",null).show();});
   button("Пульт на телефоне: сканировать QR",this::scan);button("Вставить код подключения",this::paste);return;
  }
  label(store.config().optString("name","Датчик")+(store.tv()?" • ТВ":" • телефон"),23);state=label("Запуск…",23);detail=label("",17);
  if(store.tv()){graph=new Graph();root.addView(graph,new LinearLayout.LayoutParams(-1,180));button("Подключить телефон — QR-код",this::showQr);button("1. Измерить фон — 30 секунд",()->new AlertDialog.Builder(this).setTitle("Калибровка отключит охрану").setMessage("Отойди от зоны роутер—ТВ и не двигайся 30 секунд. Дальше приложение предложит проверить проходы.").setPositiveButton("Начать",(d,w)->{permissions(true);start("calibrate");}).setNegativeButton("Отмена",null).show());passes=button("2. Проверить проходы — 30 секунд",()->start("passes"));
   button("Чувствительность",()->new AlertDialog.Builder(this).setTitle("Чувствительность").setSingleChoiceItems(new String[]{"Ниже","Обычная","Выше"},store.p.getInt("sensitivity",1),(d,w)->{Intent i=new Intent(this,GuardService.class).putExtra("action","sensitivity").putExtra("value",w);startForegroundService(i);d.dismiss();}).show());
  }
  arm=button("Включить охрану",()->start("arm"));button("Выключить / отменить отсчёт",()->start("disarm"));if(!store.tv())button("Обновить состояние ТВ",()->start("status"));
  button("Разрешения и работа в фоне",()->{permissions(store.tv());try{startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}catch(Exception e){message("Разреши работу без ограничений батареи в настройках приложения.");}});
  label("Журнал событий",21);journal=label("",16);button("Сбросить подключение",()->new AlertDialog.Builder(this).setTitle("Удалить настройки с этого устройства?").setMessage("Охрана и получение сообщений на этом устройстве остановятся. Если это ТВ, при новом подключении будет создан новый ключ.").setPositiveButton("Сбросить",(d,w)->{stopService(new Intent(this,GuardService.class));store.p.edit().clear().commit();graph=null;state=null;detail=null;arm=null;passes=null;journal=null;build();}).setNegativeButton("Отмена",null).show());
 }
 TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setPadding(0,8,0,8);root.addView(v);return v;}
 Button button(String text,Runnable run){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->run.run());root.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
 void start(String action){if(!store.ready())return;Intent i=new Intent(this,GuardService.class);if(action!=null)i.putExtra("action",action);startForegroundService(i);}
 void permissions(boolean tv){ArrayList<String> list=new ArrayList<>();if(tv&&checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")!=android.content.pm.PackageManager.PERMISSION_GRANTED)list.add("android.permission.ACCESS_FINE_LOCATION");if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)list.add("android.permission.POST_NOTIFICATIONS");if(!list.isEmpty())requestPermissions(list.toArray(new String[0]),50);}
 void scan(){if(checkSelfPermission("android.permission.CAMERA")!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.CAMERA"},70);return;}try{startActivityForResult(new Intent(this,QrActivity.class),IntentIntegrator.REQUEST_CODE);}catch(Exception e){((GuardApp)getApplication()).record(e);message("Не удалось открыть сканер. Используй вставку кода подключения.");}}
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==70){if(results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)scan();else message("Доступ к камере не разрешён. Разреши его в настройках или вставь код подключения.");}}
 void paste(){EditText input=new EditText(this);input.setHint("Вставь полный код с ТВ");new AlertDialog.Builder(this).setTitle("Подключение к ТВ").setView(input).setPositiveButton("Подключить",(d,w)->importCode(input.getText().toString())).setNegativeButton("Отмена",null).show();}
 void importCode(String raw){try{stopService(new Intent(this,GuardService.class));store.importPhone(raw.trim());build();permissions(false);start(null);handler.postDelayed(()->start("status"),2500);}catch(Exception e){message("Неверный код подключения. Используй QR-код из этого приложения на ТВ.");}}
 void showQr(){try{String value=store.config().toString();BitMatrix matrix=new MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,650,650);Bitmap bitmap=Bitmap.createBitmap(650,650,Bitmap.Config.ARGB_8888);for(int x=0;x<650;x++)for(int y=0;y<650;y++)bitmap.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);new AlertDialog.Builder(this).setTitle("На телефоне: Пульт → сканировать QR").setView(image).setMessage("Код даёт доступ к датчику. Не публикуй его.").setPositiveButton("Готово",null).setNeutralButton("Копировать код",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Wi-Fi Охрана",value));message("Код скопирован. Передай только своему телефону.");}).show();}catch(Exception e){message("Не удалось построить QR-код");}}
 void message(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("ОК",null).show();}
 void render(){if(state==null||!store.ready())return;long now=System.currentTimeMillis();boolean tv=store.tv();JSONObject remote=new JSONObject();try{remote=new JSONObject(store.p.getString("remote","{}"));}catch(Exception ignored){}
  boolean wifi=tv?store.p.getBoolean("wifi",false):remote.optBoolean("wifi");boolean calibrated=tv?store.p.getBoolean("calibrated",false):remote.optBoolean("calibrated");boolean armed=tv?store.p.getBoolean("armed",false):remote.optBoolean("armed");long until=tv?store.p.getLong("armAt",0):remote.optLong("exitUntil");long seen=store.p.getLong("lastSeen",0);boolean fresh=tv||(seen>0&&now-seen<35*60000L);int phase=store.p.getInt("phase",0);
  String text=!fresh?"Состояние ТВ не подтверждено":!wifi?"Нет измерений Wi-Fi":!calibrated?"Нужна калибровка":until>now?"Выход: "+((until-now+999)/1000)+" с":until>0?"Ожидаем подтверждение включения":armed?"Охрана включена":"Охрана выключена";
  if(tv&&phase!=0)text=phase==1?"Измеряем фон":phase==2?"Теперь проверь проходы":"Проверяем проходы";
  if(!tv&&!store.p.getString("pending","").isEmpty())text="Ожидаем подтверждение команды…";
  state.setText(text);state.setTextColor(fresh&&wifi&&calibrated&&armed&&until==0?0xff65d9a5:0xffffcf75);
  int r=tv?store.p.getInt("rssi",-127):remote.optInt("rssi",-127), f=tv?store.p.getInt("freq",0):remote.optInt("frequency",0);
  String note=store.p.getString("note","");detail.setText((wifi?r+" dBm • "+(f<3000?"2,4":"5")+" ГГц\n":"")+(store.p.getBoolean("connected",false)?"Доставка подключена":"Доставка переподключается")+(!tv?"\nПоследний ответ ТВ: "+(seen==0?"ещё нет":Math.max(0,(now-seen)/60000)+" мин назад"):"")+"\n"+note+(tv&&(phase==1||phase==3)?"\nОсталось "+Math.max(0,(store.p.getLong("phaseEnd",0)-now+999)/1000)+" с":"")+(!tv&&!remote.optString("note").isEmpty()?"\nТВ: "+remote.optString("note"):""));
  if(passes!=null)passes.setEnabled(phase==2);arm.setEnabled(fresh&&wifi&&calibrated&&(!tv||phase==0));String log=store.p.getString("journal","");journal.setText(log.isEmpty()?"Событий пока нет":log.substring(0,Math.min(6000,log.length())));if(graph!=null&&wifi){graph.add(r);}
 }
 @Override protected void onResume(){super.onResume();handler.post(refresh);}
 @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
 @Override protected void onActivityResult(int req,int result,Intent data){IntentResult scan=IntentIntegrator.parseActivityResult(req,result,data);if(scan!=null){if(scan.getContents()!=null)importCode(scan.getContents());return;}super.onActivityResult(req,result,data);}
 class Graph extends View{ArrayList<Integer> values=new ArrayList<>();Paint p=new Paint(3);Graph(){super(MainActivity.this);}void add(int r){values.add(r);if(values.size()>100)values.remove(0);invalidate();}protected void onDraw(Canvas c){c.drawColor(0xff18232d);p.setTextSize(22);int max=-25,min=-50;for(int r:values){max=Math.max(max,r+3);min=Math.min(min,r-3);}p.setColor(Color.LTGRAY);c.drawText(max+" dBm",8,25,p);c.drawText(min+" dBm",8,getHeight()-5,p);p.setColor(0xff65d9a5);p.setStrokeWidth(3);for(int j=1;j<values.size();j++){float x1=100+(j-1)/99f*(getWidth()-105),x2=100+j/99f*(getWidth()-105);float y1=15+(max-values.get(j-1))/(float)(max-min)*(getHeight()-30),y2=15+(max-values.get(j))/(float)(max-min)*(getHeight()-30);c.drawLine(x1,y1,x2,y2,p);}}}
}

