package org.wifimotion.test;
import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
import com.journeyapps.barcodescanner.*;
import com.google.zxing.ResultPoint;
import java.util.List;
public class QrActivity extends Activity {
 DecoratedBarcodeView reader;boolean finishing=false;
 @Override public void onCreate(Bundle state){super.onCreate(state);try{
  if(checkSelfPermission("android.permission.CAMERA")!=android.content.pm.PackageManager.PERMISSION_GRANTED){setResult(RESULT_CANCELED);finish();return;}
  LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);TextView hint=new TextView(this);hint.setText("Наведи камеру на QR-код на ТВ");hint.setTextSize(20);hint.setPadding(20,20,20,20);layout.addView(hint);reader=new DecoratedBarcodeView(this);layout.addView(reader,new LinearLayout.LayoutParams(-1,0,1));Button back=new Button(this);back.setText("Назад");back.setOnClickListener(v->finish());layout.addView(back);setContentView(layout);
  reader.getBarcodeView().addStateListener(new CameraPreview.StateListener(){public void previewSized(){}public void previewStarted(){}public void previewStopped(){}public void cameraClosed(){}public void cameraError(Exception e){problem(e);}});
  reader.decodeSingle(new BarcodeCallback(){public void barcodeResult(BarcodeResult result){if(finishing)return;finishing=true;setResult(RESULT_OK,new Intent().putExtra("SCAN_RESULT",result.getText()).putExtra("SCAN_RESULT_FORMAT","QR_CODE"));finish();}public void possibleResultPoints(List<ResultPoint> points){}});
 }catch(Exception e){problem(e);}}
 @Override protected void onResume(){super.onResume();if(reader!=null&&!finishing)try{reader.resume();}catch(Exception e){problem(e);}}
 @Override protected void onPause(){if(reader!=null)try{reader.pause();}catch(Exception e){((GuardApp)getApplication()).record(e);}super.onPause();}
 void problem(Exception e){((GuardApp)getApplication()).record(e);runOnUiThread(()->{if(isFinishing()||finishing)return;finishing=true;new AlertDialog.Builder(this).setMessage("Не удалось открыть камеру. Проверь разрешение и закрой другие приложения с камерой. Можно подключиться через «Вставить код подключения».").setPositiveButton("Назад",(d,w)->finish()).setOnCancelListener(d->finish()).show();});}
}
