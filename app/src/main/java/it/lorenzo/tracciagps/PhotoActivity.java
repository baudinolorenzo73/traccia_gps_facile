package it.lorenzo.tracciagps;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.location.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.JSONObject;

public final class PhotoActivity extends Activity {
    private String pending;
    private LocationListener listener;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status;
    private LocationManager manager;
    private boolean cameraActive,locateWanted;
    private File directory(){File f=new File(getFilesDir(),"photos");f.mkdirs();return f;}
    public void onCreate(Bundle b){
        setTheme(getSharedPreferences("ui",0).getBoolean("dark",true)?R.style.AppThemeDark:R.style.AppTheme);
        super.onCreate(b);manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        pending=getPreferences(0).getString("pending",null);cameraActive=b!=null&&b.getBoolean("cameraActive");
        show();
        if(b==null && !getIntent().getBooleanExtra("gallery",false) && pending==null)capture();
    }
    private void capture(){
        if(cameraActive)return;
        pending="photo_"+UUID.randomUUID()+".jpg";getPreferences(0).edit().putString("pending",pending).commit();
        Uri u=PhotoProvider.uri(this,pending);
        Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT,u).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(ClipData.newRawUri("Foto",u));
        try{cameraActive=true;startActivityForResult(i,41);}catch(ActivityNotFoundException e){cameraActive=false;reset();status.setText("Nessuna fotocamera disponibile.");}
    }
    private void reset(){pending=null;getPreferences(0).edit().remove("pending").commit();}
    private void show(){
        boolean dark=getSharedPreferences("ui",0).getBoolean("dark",true);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.bg(this));LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(Ui.dp(this,20),Ui.dp(this,20),Ui.dp(this,20),Ui.dp(this,24));scroll.addView(root);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        TextView title=Ui.label(this,"Foto del percorso",26,true);root.addView(title);
        status=Ui.label(this,"",14,false);status.setTextColor(Ui.muted(this));status.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,16));status.setText("Le coordinate vengono rilevate dopo lo scatto: resta vicino al punto fotografato. Un nuovo tentativo usa la posizione attuale, non ricostruisce quella dello scatto. GPS richiesto ±50 m, massimo 30 secondi. Le foto restano salvate anche senza fix.");root.addView(status);
        Button take=Ui.action(this,"Scatta una foto",true,()->{if(pending!=null){status.setText("Completa o conserva prima la foto in attesa.");return;}capture();});root.addView(take);
        if(pending!=null && new File(directory(),pending).length()>0){
            Button retry=Ui.action(this,"Rileva GPS qui per la foto in attesa",false,()->locate());root.addView(retry);
            Button keep=Ui.action(this,"Conserva senza posizione",false,()->{cancel();reset();show();});root.addView(keep);
        }
        File[] files=directory().listFiles((dir,n)->n.endsWith(".jpg"));if(files==null)return;
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        int shown=0;for(File f:files){if(shown++>=12){TextView more=new TextView(this);more.setText("Mostrate le 12 foto più recenti. Esporta ZIP per tutte le foto.");root.addView(more);break;}
            ImageView image=new ImageView(this);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>700)o.inSampleSize*=2;o.inJustDecodeBounds=false;Bitmap bmp=BitmapFactory.decodeFile(f.getPath(),o);
            if(bmp!=null){try{ExifInterface ex=new ExifInterface(f);int orientation=ex.getAttributeInt(ExifInterface.TAG_ORIENTATION,1);Matrix m=new Matrix();if(orientation==2)m.setScale(-1,1);if(orientation==3)m.setRotate(180);if(orientation==4)m.setScale(1,-1);if(orientation==5){m.setRotate(90);m.postScale(-1,1);}if(orientation==6)m.setRotate(90);if(orientation==7){m.setRotate(270);m.postScale(-1,1);}if(orientation==8)m.setRotate(270);image.setImageBitmap(Bitmap.createBitmap(bmp,0,0,bmp.getWidth(),bmp.getHeight(),m,true));}catch(IOException e){image.setImageBitmap(bmp);}}
            image.setAdjustViewBounds(true);image.setMaxHeight(Ui.dp(this,240));image.setClipToOutline(true);image.setBackground(Ui.shape(this,Ui.panel(this),18));LinearLayout.LayoutParams imageLayout=new LinearLayout.LayoutParams(-1,-2);imageLayout.topMargin=Ui.dp(this,20);root.addView(image,imageLayout);
            image.setOnClickListener(v->{try{Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(PhotoProvider.uri(this,f.getName()),"image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(ActivityNotFoundException e){status.setText("Nessun visualizzatore foto installato.");}});
            TextView info=Ui.label(this,"",13,false);info.setTextColor(Ui.muted(this));info.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,8));String photoDate=new java.text.SimpleDateFormat("dd MMM · HH:mm",Locale.ITALY).format(new Date(f.lastModified()));
            try{JSONObject j=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(directory(),f.getName()+".json").toPath()),java.nio.charset.StandardCharsets.UTF_8));info.setText(String.format(Locale.ITALY,"%.6f, %.6f · ±%.0f m\n%s",j.getDouble("lat"),j.getDouble("lon"),j.getDouble("accuracy"),photoDate));}catch(Exception e){info.setText(photoDate+" · Foto senza posizione");}root.addView(info);
        }
    }
    private static String rational(double value){value=Math.abs(value);int deg=(int)value;double minutes=(value-deg)*60;int min=(int)minutes;long sec=Math.round((minutes-min)*60*1000000);return deg+"/1,"+min+"/1,"+sec+"/1000000";}
    private void locate(){
        if(listener!=null||pending==null)return;
        if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},42);return;}
        status.setText("Ricerca GPS per la foto… resta vicino al punto dello scatto.");
        listener=new LocationListener(){public void onLocationChanged(Location p){
            if(!GpsFilter.valid(p.getLatitude(),p.getLongitude(),p.hasAccuracy()?p.getAccuracy():Double.NaN,SystemClock.elapsedRealtimeNanos()-p.getElapsedRealtimeNanos(),50,10_000_000_000L))return;
            String name=pending;cancel();File file=new File(directory(),name);
            try{
                ExifInterface ex=new ExifInterface(file);ex.setAttribute(ExifInterface.TAG_GPS_LATITUDE,rational(p.getLatitude()));ex.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF,p.getLatitude()<0?"S":"N");ex.setAttribute(ExifInterface.TAG_GPS_LONGITUDE,rational(p.getLongitude()));ex.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF,p.getLongitude()<0?"W":"E");if(p.hasAltitude()){ex.setAttribute(ExifInterface.TAG_GPS_ALTITUDE,Math.round(Math.abs(p.getAltitude())*100)+"/100");ex.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF,p.getAltitude()<0?"1":"0");}ex.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD,"GPS");ex.saveAttributes();
                JSONObject j=new JSONObject();j.put("lat",p.getLatitude());j.put("lon",p.getLongitude());j.put("accuracy",p.getAccuracy());j.put("fixTime",p.getTime());j.put("photo",name);
                android.util.AtomicFile sidecar=new android.util.AtomicFile(new File(directory(),name+".json"));FileOutputStream out=sidecar.startWrite();try{out.write(j.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));sidecar.finishWrite(out);}catch(Exception e){sidecar.failWrite(out);throw e;}
                // Photo EXIF + sidecar are authoritative; no duplicate waypoint on GPS retry.
                reset();show();status.setText("Foto salvata con GPS · precisione ±"+Math.round(p.getAccuracy())+" m.");
            }catch(Exception e){status.setText("Foto conservata; geolocalizzazione incompleta: "+e.getMessage());}
        }public void onProviderDisabled(String p){}public void onProviderEnabled(String p){}public void onStatusChanged(String p,int s,Bundle b){}};
        try{manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,listener,Looper.getMainLooper());LocationListener current=listener;handler.postDelayed(()->{if(listener==current){cancel();status.setText("Nessun fix entro 30 secondi. Foto conservata: puoi riprovare il GPS o conservarla senza posizione.");}},30000);}catch(Exception e){cancel();status.setText("GPS non disponibile. Foto conservata; attiva la posizione e riprova.");}
    }
    private void cancel(){if(listener!=null){manager.removeUpdates(listener);listener=null;}handler.removeCallbacksAndMessages(null);}
    protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);if(r==41){cameraActive=false;if(result==RESULT_OK && pending!=null && new File(directory(),pending).length()>0){locateWanted=true;show();}else{if(pending!=null)new File(directory(),pending).delete();reset();show();}}}
    public void onRequestPermissionsResult(int r,String[] p,int[] result){super.onRequestPermissionsResult(r,p,result);if(r==42 && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED)locate();else status.setText("Foto conservata senza GPS: serve il permesso Posizione precisa.");}
    protected void onResume(){super.onResume();if(!cameraActive&&pending!=null){if(new File(directory(),pending).length()>0){if(locateWanted){locateWanted=false;locate();}}else{new File(directory(),pending).delete();reset();show();}}}
    protected void onPause(){cancel();super.onPause();}
    protected void onSaveInstanceState(Bundle b){b.putBoolean("cameraActive",cameraActive);super.onSaveInstanceState(b);}
}
