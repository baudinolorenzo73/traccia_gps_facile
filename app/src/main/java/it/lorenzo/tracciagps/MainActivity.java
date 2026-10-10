package it.lorenzo.tracciagps;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends MapActivity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private TextView state,stats,battery,waypoints,updateState,distanceValue,timeValue,countValue;
    private Button record,point,export,clear;
    private Store store;
    private LocationManager manager;
    private LocationListener single;
    private String pendingAction;
    private boolean starting=false;
    private static volatile boolean exporting=false;
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,1000);}};
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);store=Store.get(this);manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(!TrackingService.running){store.recover();TrackingService.status="Pronto · premi Avvia per registrare";}
        if(b!=null)pendingAction=b.getString("pendingAction");
        LinearLayout root=Ui.vertical(this);root.setBackgroundColor(Ui.bg(this));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(16),dp(9),dp(12),dp(9));
        View mark=new View(this){android.graphics.Paint p=new android.graphics.Paint(3);protected void onDraw(android.graphics.Canvas c){float w=getWidth(),h=getHeight();p.setColor(Ui.accent(MainActivity.this));p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setStrokeCap(android.graphics.Paint.Cap.ROUND);android.graphics.Path path=new android.graphics.Path();path.moveTo(w*.05f,h*.75f);path.lineTo(w*.43f,h*.2f);path.lineTo(w*.67f,h*.57f);path.lineTo(w*.8f,h*.39f);path.lineTo(w*.98f,h*.75f);c.drawPath(path,p);}};header.addView(mark,new LinearLayout.LayoutParams(dp(34),dp(34)));
        LinearLayout brand=Ui.vertical(this);brand.setPadding(dp(12),0,0,0);TextView title=Ui.label(this,"Traccia GPS",20,true);brand.addView(title);updateState=Ui.label(this,UpdateActivity.version(this),11,false);updateState.setTextColor(Ui.muted(this));brand.addView(updateState);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));brand.setOnClickListener(v->startActivity(new Intent(this,UpdateActivity.class)));
        Button options=Ui.action(this,"•••",false,()->showOptions());options.setTextSize(20);options.setContentDescription("Opzioni, esportazione, tema e aggiornamenti");header.addView(options,new LinearLayout.LayoutParams(dp(48),dp(44)));root.addView(header,new LinearLayout.LayoutParams(-1,-2));
        View map=mapContent();root.addView(map,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bottom=Ui.vertical(this);bottom.setPadding(dp(16),dp(10),dp(16),dp(12));root.addView(bottom,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout metrics=new LinearLayout(this);metrics.setPadding(0,dp(2),0,dp(8));distanceValue=metric(metrics,"DISTANZA","0,00 km");timeValue=metric(metrics,"TEMPO","0:00:00");countValue=metric(metrics,"PUNTI GPS","0");bottom.addView(metrics);
        state=Ui.label(this,"Pronto a registrare",12,false);state.setTextColor(Ui.muted(this));state.setMaxLines(1);state.setEllipsize(android.text.TextUtils.TruncateAt.END);bottom.addView(state);state.setOnClickListener(v->message(TrackingService.status));
        record=Ui.action(this,"Avvia registrazione",true,()->{if(TrackingService.running)new AlertDialog.Builder(this).setTitle("Fermare la traccia?").setMessage("I punti registrati resteranno salvati.").setPositiveButton("Ferma",(d,w)->stopService(new Intent(this,TrackingService.class))).setNegativeButton("Continua",null).show();else permissions("track");});record.setTextSize(16);LinearLayout.LayoutParams recordLayout=new LinearLayout.LayoutParams(-1,dp(52));recordLayout.topMargin=dp(8);bottom.addView(record,recordLayout);
        LinearLayout quick=new LinearLayout(this);LinearLayout.LayoutParams quickLayout=new LinearLayout.LayoutParams(-1,dp(46));quickLayout.topMargin=dp(8);bottom.addView(quick,quickLayout);
        Button photo=Ui.action(this,"Foto GPS",false,()->startActivity(new Intent(this,PhotoActivity.class)));point=Ui.action(this,"Salva punto",false,()->permissions("point"));Button gallery=Ui.action(this,"Galleria",false,()->startActivity(new Intent(this,PhotoActivity.class).putExtra("gallery",true)));for(Button button:new Button[]{photo,point,gallery}){button.setTextSize(12);LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(0,-1,1);if(quick.getChildCount()>0)item.leftMargin=dp(7);quick.addView(button,item);}
    }
    private TextView metric(LinearLayout row,String label,String value){LinearLayout block=Ui.vertical(this);TextView caption=Ui.label(this,label,10,true);caption.setLetterSpacing(.08f);caption.setTextColor(Ui.muted(this));block.addView(caption);TextView number=Ui.label(this,value,20,true);number.setMaxLines(1);number.setAutoSizeTextTypeUniformWithConfiguration(13,20,1,android.util.TypedValue.COMPLEX_UNIT_SP);block.addView(number);row.addView(block,new LinearLayout.LayoutParams(0,-2,1));return number;}
    private void showOptions(){new AlertDialog.Builder(this).setTitle("Il tuo percorso").setItems(new String[]{"Esporta tracce e punti in GPX","Esporta tracce e foto in ZIP","Mostra punti salvati",Ui.dark(this)?"Passa al tema chiaro":"Passa al tema scuro","Versione e aggiornamenti","Batteria e registrazione a schermo spento","Cancella tracce e punti"},(d,w)->{if(w==0)chooseExport(30);if(w==1)chooseExport(31);if(w==2)showPoints();if(w==3){getSharedPreferences("ui",0).edit().putBoolean("dark",!Ui.dark(this)).apply();recreate();}if(w==4)startActivity(new Intent(this,UpdateActivity.class));if(w==5)showBattery();if(w==6)clearData();}).show();}
    private void chooseExport(int request){if(exporting){message("Esportazione già in corso");return;}if(request==30&&!store.hasPoints()){message("Nessun punto da esportare");return;}String name=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ITALY).format(new Date());Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(request==31?"application/zip":"application/gpx+xml").putExtra(Intent.EXTRA_TITLE,(request==31?"escursione_":"traccia_")+name+(request==31?".zip":".gpx"));try{startActivityForResult(i,request);}catch(ActivityNotFoundException e){message("Gestore documenti non disponibile");}}
    private void clearData(){if(TrackingService.running||starting||single!=null||exporting){message("Ferma prima la traccia e attendi le operazioni in corso");return;}new AlertDialog.Builder(this).setTitle("Cancella tracce e punti?").setMessage("Esporta prima il GPX. Le foto restano conservate. La cancellazione non si può annullare.").setPositiveButton("Cancella",(d,w)->{if(TrackingService.running||starting||single!=null||exporting)return;try{store.clear();render();}catch(Exception e){message("Cancellazione fallita: "+e.getMessage());}}).setNegativeButton("Annulla",null).show();}
    private void showPoints(){ScrollView scroll=new ScrollView(this);LinearLayout content=Ui.vertical(this);content.setPadding(dp(20),dp(12),dp(20),dp(16));scroll.addView(content);waypoints=text(content,store.waypoints(),14,false);waypoints.setTextIsSelectable(true);new AlertDialog.Builder(this).setTitle("Punti salvati").setView(scroll).setPositiveButton("Chiudi",(d,w)->waypoints=null).show();}
    private void showBattery(){ScrollView scroll=new ScrollView(this);LinearLayout content=Ui.vertical(this);content.setPadding(dp(20),dp(12),dp(20),dp(16));scroll.addView(content);battery=text(content,"",14,false);text(content,"Per registrare a schermo spento scegli Batteria → Senza restrizioni. Su Xiaomi/HyperOS verifica anche l’avvio automatico. Non usare Arresto forzato o strumenti di pulizia. Dopo un riavvio avvia una nuova traccia. La notifica mostra lo stato della registrazione.",14,false);button(content,"Impostazioni dell’app",()->openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));button(content,"Ottimizzazione batteria",()->openSettings(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));button(content,"Risparmio energetico",()->openSettings(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)));render();new AlertDialog.Builder(this).setTitle("Registrazione in background").setView(scroll).setPositiveButton("Chiudi",(d,w)->battery=null).show();}
    private TextView text(LinearLayout l,String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(getSharedPreferences("ui",0).getBoolean("dark",true)?Color.rgb(229,242,236):Color.rgb(29,55,46));if(bold)t.setTypeface(null,1);t.setPadding(0,dp(9),0,dp(9));l.addView(t);return t;}
    private Button button(LinearLayout l,String label,Runnable action){Button b=Ui.action(this,label,false,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);l.addView(b,p);return b;}
    private void openSettings(Intent i){try{startActivity(i);}catch(ActivityNotFoundException e){message("Menu non disponibile: apri Impostazioni → App → Traccia GPS Offline → Batteria");}}
    private void message(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show();}
    private void render(){
        if(state==null)return;
        String update=UpdateActivity.summary(this);updateState.setText(UpdateActivity.version(this)+(update.startsWith("Aggiornamento disponibile")?" · aggiornamento disponibile":""));
        state.setText(TrackingService.status);Store.Metrics values=store.metrics();distanceValue.setText(String.format(Locale.ITALY,"%.2f km",values.distance/1000));long sec=values.duration/1000;timeValue.setText(String.format(Locale.ITALY,"%d:%02d:%02d",sec/3600,sec/60%60,sec%60));countValue.setText(""+values.trackPoints);if(waypoints!=null)waypoints.setText(store.waypoints());
        record.setText(TrackingService.running?"Ferma registrazione":starting?"Avvio in corso…":"Avvia registrazione");record.setEnabled(!starting);point.setEnabled(single==null);
        record.setBackgroundTintList(android.content.res.ColorStateList.valueOf(TrackingService.running?Color.parseColor("#F1AE97"):Ui.accent(this)));record.setTextColor(TrackingService.running?Color.parseColor("#341A10"):Ui.dark(this)?Color.parseColor("#09281F"):Color.WHITE);
        if(battery==null)return;
        PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);
        String power=p.isPowerSaveMode()?"⚠ Risparmio energetico ATTIVO: disattivalo.":"Risparmio energetico generale disattivato.";
        power+=p.isIgnoringBatteryOptimizations(getPackageName())?"\nEsclusione dall’ottimizzazione Android attiva.":"\n⚠ App soggetta all’ottimizzazione Android: controlla le impostazioni.";
        battery.setText(power+"\nLe restrizioni aggiuntive del produttore vanno verificate manualmente.");
    }
    private void permissions(String action){
        pendingAction=action;
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},10);return;}
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED && !getPreferences(0).getBoolean("notificationAsked",false)){
            getPreferences(0).edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);return;
        }
        pendingAction=null;
        if(!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)){new AlertDialog.Builder(this).setMessage("Attiva la posizione GPS e riprova.").setPositiveButton("Impostazioni",(d,w)->openSettings(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).setNegativeButton("Annulla",null).show();return;}
        if("track".equals(action)){
            if(TrackingService.running||starting)return;
            Runnable start=()->{try{starting=true;render();startForegroundService(new Intent(this,TrackingService.class));handler.postDelayed(()->{starting=false;render();},1000);}catch(Exception e){starting=false;message("Avvio fallito: "+e.getMessage());render();}};
            PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);
            if(p.isPowerSaveMode() || !p.isIgnoringBatteryOptimizations(getPackageName()))new AlertDialog.Builder(this).setTitle("Controlla la batteria").setMessage("Per registrare a schermo spento disattiva il risparmio energetico e scegli Senza restrizioni per questa app.").setPositiveButton("Impostazioni",(d,w)->openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNeutralButton("Avvia comunque",(d,w)->start.run()).setNegativeButton("Annulla",null).show();else start.run();
        }else if("point".equals(action))singlePoint();
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] results){super.onRequestPermissionsResult(r,p,results);
        if(r==10){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){pendingAction=null;new AlertDialog.Builder(this).setMessage("Serve la posizione precisa. Apri Autorizzazioni → Posizione e consenti durante l’uso dell’app, con posizione precisa.").setPositiveButton("Impostazioni",(d,w)->openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("Annulla",null).show();return;}}
        if(r==11 && Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)Toast.makeText(this,"Notifiche disattivate: non vedrai il comando Ferma nella tendina",Toast.LENGTH_LONG).show();
        if(pendingAction!=null)permissions(pendingAction);
    }
    private void singlePoint(){
        if(single!=null)return;
        single=new LocationListener(){
            public void onLocationChanged(Location p){
                if(single!=this || !GpsFilter.valid(p.getLatitude(),p.getLongitude(),p.hasAccuracy()?p.getAccuracy():Double.NaN,SystemClock.elapsedRealtimeNanos()-p.getElapsedRealtimeNanos(),50,10000000000L))return;
                cancelSingle();
                String name="Punto "+new SimpleDateFormat("dd/MM HH:mm:ss",Locale.ITALY).format(new Date(p.getTime()));
                try{store.add(p,null,name,0,false);message("Punto salvato · precisione ±"+Math.round(p.getAccuracy())+" m. Usa Esporta per salvarlo nel GPX.");}catch(Exception e){message("Salvataggio fallito: "+e.getMessage());}render();
            }
            public void onProviderDisabled(String p){} public void onProviderEnabled(String p){} public void onStatusChanged(String p,int s,Bundle b){}
        };
        point.setText("Ricerca GPS…");point.setEnabled(false);
        try{manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,single,Looper.getMainLooper());
            LocationListener request=single;handler.postDelayed(()->{if(single==request){cancelSingle();message("Nessun fix preciso entro 30 secondi. Vai all’aperto e riprova.");}},30000);
        }catch(Exception e){cancelSingle();message("GPS non disponibile: "+e.getMessage());}
    }
    private void cancelSingle(){if(single!=null){manager.removeUpdates(single);single=null;}if(point!=null){point.setText("Salva punto");point.setEnabled(true);}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if((request==30||request==31) && result==RESULT_OK && data!=null && data.getData()!=null){if(exporting){message("Esportazione già in corso");return;}Uri uri=data.getData();exporting=true;render();
            io.execute(()->{String outcome;try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                if(out==null)throw new IOException("Destinazione non disponibile");if(request==31){PhotoFiles.archive(this,store,out);}else{try(Writer w=new OutputStreamWriter(out,java.nio.charset.StandardCharsets.UTF_8)){store.export(w);}}outcome=request==31?"ZIP esportato: tracce, foto e coordinate.":"GPX esportato correttamente.";
            }catch(Exception e){outcome="Esportazione fallita: "+e.getMessage()+". I dati nell’app sono conservati; riprova.";}
                String resultText=outcome;runOnUiThread(()->{exporting=false;if(!isDestroyed()){render();message(resultText);}});
            });
        }
    }
    @Override protected void onResume(){super.onResume();UpdateActivity.autoCheck(this);handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(refresh);cancelSingle();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("pendingAction",pendingAction);}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);cancelSingle();io.shutdown();super.onDestroy();}
}
