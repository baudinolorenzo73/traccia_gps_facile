package it.lorenzo.tracciagps;

import android.app.*;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.graphics.*;
import android.location.*;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;

/** Reusable map screen: the home Activity embeds this same map, not a second renderer. */
public class MapActivity extends Activity {
    private TileView map;
    private FrameLayout mapRoot;
    private TextView status,credits;
    private Button onlineButton,offlineButton;
    private LinearLayout notice;
    private final ExecutorService importer=Executors.newSingleThreadExecutor();
    private final Handler mapHandler=new Handler(Looper.getMainLooper());
    private boolean offline,diagnosing;
    private TileSource source;
    private LocationListener locate;
    private static final java.util.concurrent.atomic.AtomicBoolean importing=new java.util.concurrent.atomic.AtomicBoolean(false);
    private String lastError="";private int readyTiles,totalTiles,minZoom=0,maxZoom=19,zoomMask=(1<<20)-1;
    private final Runnable refreshMap=new Runnable(){public void run(){map.points=Store.get(MapActivity.this).mapPoints();map.photos=photoPoints();map.invalidate();mapHandler.postDelayed(this,3000);}};
    private android.content.SharedPreferences mapPrefs(){return getSharedPreferences("map",0);}
    private File offlineFile(){return new File(getFilesDir(),"offline.mbtiles");}
    protected View mapContent(){if(mapRoot.getParent()!=null)((ViewGroup)mapRoot.getParent()).removeView(mapRoot);return mapRoot;}
    public void onCreate(Bundle b){
        setTheme(Ui.dark(this)?R.style.AppThemeDark:R.style.AppTheme);super.onCreate(b);
        if(!mapPrefs().contains("offline")){android.content.SharedPreferences old=getSharedPreferences(MapActivity.class.getName(),0);mapPrefs().edit().putBoolean("offline",old.getBoolean("offline",false)).putString("attribution",old.getString("attribution","")).apply();}
        offline=mapPrefs().getBoolean("offline",false);source=TileSource.from(mapPrefs().getString("source","TOPO"));
        minZoom=mapPrefs().getInt("minZoom",0);maxZoom=mapPrefs().getInt("maxZoom",19);zoomMask=mapPrefs().getInt("zoomMask",(1<<20)-1);
        mapRoot=new FrameLayout(this);mapRoot.setBackgroundColor(Ui.bg(this));map=new TileView();mapRoot.addView(map,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout modes=new LinearLayout(this);modes.setPadding(dp(4),dp(4),dp(4),dp(4));Ui.card(modes,18);
        onlineButton=Ui.action(this,"Online",false,()->setOffline(false));offlineButton=Ui.action(this,"Offline",false,()->{if(!offlineFile().exists()){new AlertDialog.Builder(this).setTitle("Mappa offline").setMessage("Importa un MBTiles raster della zona. Senza il file la cartografia offline non è disponibile.").setPositiveButton("Importa",(d,w)->importMap()).setNegativeButton("Annulla",null).show();}else setOffline(true);});
        modes.addView(onlineButton,new LinearLayout.LayoutParams(dp(84),dp(40)));modes.addView(offlineButton,new LinearLayout.LayoutParams(dp(84),dp(40)));
        FrameLayout.LayoutParams modeLayout=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);modeLayout.setMargins(dp(12),dp(12),0,0);mapRoot.addView(modes,modeLayout);
        Button layers=Ui.action(this,"Carte",false,()->mapMenu());FrameLayout.LayoutParams layerLayout=new FrameLayout.LayoutParams(dp(70),dp(48),Gravity.TOP|Gravity.END);layerLayout.setMargins(0,dp(12),dp(12),0);mapRoot.addView(layers,layerLayout);
        notice=Ui.vertical(this);notice.setPadding(dp(12),dp(8),dp(12),dp(8));Ui.card(notice,14);status=Ui.label(this,"Caricamento mappa…",12,false);notice.addView(status);
        FrameLayout.LayoutParams noticeLayout=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);noticeLayout.setMargins(dp(12),dp(68),dp(12),0);mapRoot.addView(notice,noticeLayout);
        LinearLayout tools=Ui.vertical(this);Ui.card(tools,16);for(String label:new String[]{"+","−","◎"}){Button tool=Ui.action(this,label,false,()->{if(label.equals("+"))map.zoom(1);else if(label.equals("−"))map.zoom(-1);else currentPosition();});tool.setTextSize(label.equals("◎")?25:24);tool.setContentDescription(label.equals("+")?"Ingrandisci mappa":label.equals("−")?"Riduci mappa":"Centra sulla mia posizione GPS");tools.addView(tool,new LinearLayout.LayoutParams(dp(50),dp(50)));}
        FrameLayout.LayoutParams toolsLayout=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);toolsLayout.setMargins(0,0,dp(12),dp(46));mapRoot.addView(tools,toolsLayout);
        credits=Ui.label(this,"© OpenStreetMap contributors",10,false);credits.setPadding(dp(8),dp(5),dp(8),dp(5));credits.setBackground(Ui.shape(this,Ui.panel(this),8));credits.setMaxLines(2);credits.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(offline?"https://www.openstreetmap.org/copyright":source.license)));}catch(ActivityNotFoundException ignored){}});
        FrameLayout.LayoutParams creditLayout=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);creditLayout.setMargins(dp(10),0,dp(70),dp(8));mapRoot.addView(credits,creditLayout);
        setContentView(mapRoot);mapRoot.setOnApplyWindowInsetsListener((v,i)->{if(getClass()==MapActivity.class)v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        map.points=Store.get(this).mapPoints();map.photos=photoPoints();
        if(mapPrefs().contains("lat")){map.lat=Double.longBitsToDouble(mapPrefs().getLong("lat",Double.doubleToLongBits(45.0677)));map.lon=Double.longBitsToDouble(mapPrefs().getLong("lon",Double.doubleToLongBits(7.6825)));map.z=mapPrefs().getInt("zoom",12);}else map.centerSaved();
        if(b!=null){map.lat=b.getDouble("mapLat",map.lat);map.lon=b.getDouble("mapLon",map.lon);map.z=b.getInt("mapZoom",map.z);}
        if(offline)map.z=TilePolicy.closestZoom(map.z,zoomMask);else map.z=TilePolicy.zoomWithin(map.z,0,source.maxZoom);updateMode();updateNotice();
    }
    private int dp(int n){return Ui.dp(this,n);}
    private void setOffline(boolean value){offline=value;mapPrefs().edit().putBoolean("offline",offline).apply();lastError="";readyTiles=totalTiles=0;if(offline)map.z=TilePolicy.closestZoom(map.z,zoomMask);else map.z=TilePolicy.zoomWithin(map.z,0,source.maxZoom);map.reset();updateMode();updateNotice();}
    private void updateMode(){onlineButton.setTextColor(offline?Ui.muted(this):Ui.accent(this));offlineButton.setTextColor(offline?Ui.accent(this):Ui.muted(this));onlineButton.setTypeface(null,offline?0:1);offlineButton.setTypeface(null,offline?1:0);credits.setText(offline?mapPrefs().getString("attribution","Mappa locale · controlla la licenza del file"):source.credit);}
    private void updateNotice(){
        String text="";
        if(offline&&!offlineFile().exists())text="Nessuna mappa offline importata · Carte → Importa MBTiles";
        else if(!lastError.isEmpty())text=lastError+(readyTiles>0?" · alcune tessere già visibili":"");
        else if(totalTiles==0||readyTiles==0)text=offline?"Caricamento mappa locale…":"Caricamento cartografia online…";
        else if(readyTiles<totalTiles)text="Caricamento mappa · "+readyTiles+"/"+totalTiles+" tessere";
        notice.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE);status.setText(text);
    }
    private void chooseSource(){new AlertDialog.Builder(this).setTitle("Fonte della mappa online").setSingleChoiceItems(new String[]{"OpenTopoMap · sentieri e rilievi","OpenStreetMap · strade e luoghi"},source==TileSource.TOPO?0:1,(d,w)->{source=w==0?TileSource.TOPO:TileSource.OSM;mapPrefs().edit().putString("source",source.name()).apply();setOffline(false);d.dismiss();}).setNegativeButton("Annulla",null).show();}
    private void mapMenu(){new AlertDialog.Builder(this).setTitle("Cartografia").setItems(new String[]{"Importa MBTiles offline","Ricarica tessere visibili","Centra sull’ultimo punto salvato","Centra sulla mappa importata","Diagnostica connessione","Fonte online · "+source.label},(d,w)->{if(w==0)importMap();if(w==1){lastError="";map.reset();updateNotice();}if(w==2)map.centerSaved();if(w==3){if(offlineFile().exists()){map.lat=Double.longBitsToDouble(mapPrefs().getLong("offlineLat",Double.doubleToLongBits(45.0677)));map.lon=Double.longBitsToDouble(mapPrefs().getLong("offlineLon",Double.doubleToLongBits(7.6825)));map.z=mapPrefs().getInt("offlineZoom",minZoom);if(!offline)setOffline(true);map.invalidate();}else lastError="Importa prima una mappa offline";updateNotice();}if(w==4)diagnose();if(w==5)chooseSource();}).show();}
    private void importMap(){if(importing.get()){Toast.makeText(this,"Importazione in corso…",Toast.LENGTH_SHORT).show();return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");try{startActivityForResult(i,51);}catch(ActivityNotFoundException e){lastError="Gestore file non disponibile";updateNotice();}}
    private List<double[]> photoPoints(){List<double[]> points=new ArrayList<>();File[] files=new File(getFilesDir(),"photos").listFiles((d,n)->n.endsWith(".json"));if(files!=null)for(File f:files)try{org.json.JSONObject j=new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()),java.nio.charset.StandardCharsets.UTF_8));points.add(new double[]{j.getDouble("lat"),j.getDouble("lon")});}catch(Exception ignored){}return points;}
    private void currentPosition(){
        if(locate!=null)return;
        if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},21);return;}
        LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);lastError="Ricerca della tua posizione GPS…";updateNotice();
        locate=new LocationListener(){public void onLocationChanged(Location p){if(!GpsFilter.valid(p.getLatitude(),p.getLongitude(),p.hasAccuracy()?p.getAccuracy():Double.NaN,SystemClock.elapsedRealtimeNanos()-p.getElapsedRealtimeNanos(),100,15_000_000_000L))return;cancelLocate();map.lat=p.getLatitude();map.lon=p.getLongitude();map.live=new Location(p);lastError="";map.invalidate();updateNotice();}public void onProviderEnabled(String p){}public void onProviderDisabled(String p){}public void onStatusChanged(String p,int s,Bundle b){}};
        try{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,locate,Looper.getMainLooper());LocationListener request=locate;mapHandler.postDelayed(()->{if(locate==request){cancelLocate();lastError="GPS non agganciato · vai all’aperto e riprova ◎";updateNotice();}},30000);}catch(Exception e){cancelLocate();lastError="Posizione non disponibile: attiva GPS e riprova";updateNotice();}
    }
    private void cancelLocate(){if(locate!=null){((LocationManager)getSystemService(LOCATION_SERVICE)).removeUpdates(locate);locate=null;}}
    public void onRequestPermissionsResult(int request,String[] p,int[] result){super.onRequestPermissionsResult(request,p,result);if(request==21){if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED)currentPosition();else{lastError="Posizione non autorizzata · la mappa online rimane utilizzabile";updateNotice();}}}
    private void diagnose(){
        if(diagnosing)return;if(offline){new AlertDialog.Builder(this).setMessage("Sei in Offline: in questa modalità non viene contattato il server. Seleziona Online per provare la rete.").setPositiveButton("OK",null).show();return;}diagnosing=true;
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);NetworkCapabilities cap=cm.getNetworkCapabilities(cm.getActiveNetwork());String network=cap==null?"Nessuna rete attiva":cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)?"Rete Android con accesso Internet":"Rete connessa: accesso Internet non validato (possibile portale Wi-Fi)";
        int z=map.z,x=Math.floorMod((int)(MapMath.x(map.lon,z)/256),1<<z),y=(int)(MapMath.y(map.lat,z)/256);TileSource selected=source;String endpoint=selected.endpoint(z,x,y);
        lastError="Verifica connessione cartografica…";updateNotice();importer.execute(()->{String report;Set<HttpURLConnection> active=ConcurrentHashMap.newKeySet();try{byte[] data=TileTransport.fetch(endpoint,active);Bitmap sample=map.decode(data);if(sample==null)throw new IOException("Il server ha restituito una risposta che non è un’immagine valida");sample.recycle();report=network+"\n"+selected.label+": HTTP 200, immagine valida ("+data.length+" byte).\nNessuna API key richiesta.\nSe mancano tessere: Carte → Ricarica tessere visibili.";}catch(Exception e){report=network+"\n"+TileTransport.message(e)+"\nControlla dati mobili/Wi-Fi, accesso rete consentito per l’app, eventuale VPN/DNS privato, data e ora.";}String result=report;runOnUiThread(()->{diagnosing=false;if(isDestroyed())return;lastError="";updateNotice();new AlertDialog.Builder(this).setTitle("Diagnostica mappa").setMessage(result).setPositiveButton("OK",null).show();});});
    }
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=51||result!=RESULT_OK||data==null||data.getData()==null)return;
        android.net.Uri uri=data.getData();if(!importing.compareAndSet(false,true))return;lastError="Verifica e importazione della mappa…";updateNotice();
        importer.execute(()->{File temp=new File(getFilesDir(),"offline-import.mbtiles");String error=null,attribution="";double clat=45.0677,clon=7.6825;int zoom=12,min=0,max=19,mask=0;boolean explicitCenter=false;String bounds=null;
            try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(temp)){if(in==null)throw new IOException("File non disponibile");byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);out.getFD().sync();}catch(Exception e){error=e.getMessage();}
            if(error==null)try(SQLiteDatabase db=SQLiteDatabase.openDatabase(temp.getPath(),null,SQLiteDatabase.OPEN_READONLY)){
                try(Cursor range=db.rawQuery("SELECT MIN(zoom_level),MAX(zoom_level) FROM tiles",null)){if(!range.moveToFirst()||range.isNull(0))throw new IOException("Mappa vuota");min=range.getInt(0);max=range.getInt(1);if(min<0||max>19)throw new IOException("Zoom supportati: 0–19");zoom=min;}
                try(Cursor levels=db.rawQuery("SELECT DISTINCT zoom_level FROM tiles",null)){while(levels.moveToNext())mask|=1<<levels.getInt(0);}
                try(Cursor c=db.rawQuery("SELECT tile_column,tile_row,tile_data FROM tiles WHERE zoom_level=? LIMIT 1",new String[]{""+zoom})){if(!c.moveToFirst())throw new IOException("Mappa vuota");byte[] bytes=c.getBlob(2);Bitmap sample=map.decode(bytes);if(sample==null)throw new IOException("Serve MBTiles raster PNG/JPEG/WebP, non PBF");sample.recycle();clon=MapMath.lon((c.getInt(0)+.5)*256,zoom);clat=MapMath.lat(((1<<zoom)-1-c.getInt(1)+.5)*256,zoom);}
                try(Cursor c=db.rawQuery("SELECT name,value FROM metadata",null)){while(c.moveToNext()){
                    if("scheme".equals(c.getString(0))&&"xyz".equalsIgnoreCase(c.getString(1)))throw new IOException("MBTiles deve usare righe TMS");
                    if("attribution".equals(c.getString(0)))attribution=android.text.Html.fromHtml(c.getString(1),0).toString();
                    if("bounds".equals(c.getString(0)))bounds=c.getString(1);
                    if("center".equals(c.getString(0)))try{String[] a=c.getString(1).split(",");clon=Double.parseDouble(a[0]);clat=Double.parseDouble(a[1]);if(a.length>2)zoom=(int)Double.parseDouble(a[2]);explicitCenter=true;}catch(Exception ignored){}
                }}catch(SQLiteException ignored){}
                if(!explicitCenter&&bounds!=null)try{String[] bbox=bounds.split(",");clon=(Double.parseDouble(bbox[0])+Double.parseDouble(bbox[2]))/2;clat=(Double.parseDouble(bbox[1])+Double.parseDouble(bbox[3]))/2;}catch(Exception ignored){}
                if(!Double.isFinite(clat)||Math.abs(clat)>85.051129||!Double.isFinite(clon)||Math.abs(clon)>180)throw new IOException("Centro mappa non valido");
            }catch(Exception e){error=e.getMessage();}
            final String err=error,credit=attribution;final double centerLat=clat,centerLon=clon;final int minZ=min,maxZ=max,maskZ=mask,centerZ=TilePolicy.closestZoom(zoom,mask);
            runOnUiThread(()->{importing.set(false);if(err!=null){temp.delete();if(!isDestroyed()){lastError="Importazione fallita: "+err+". Mappa precedente conservata.";updateNotice();}return;}if(!temp.renameTo(offlineFile())){temp.delete();if(!isDestroyed()){lastError="Sostituzione mappa non riuscita";updateNotice();}return;}
                mapPrefs().edit().putString("attribution",credit.isEmpty()?"Mappa locale · attribuzione non dichiarata nel file":credit).putInt("zoomMask",maskZ).putInt("minZoom",minZ).putInt("maxZoom",maxZ).putLong("offlineLat",Double.doubleToLongBits(centerLat)).putLong("offlineLon",Double.doubleToLongBits(centerLon)).putInt("offlineZoom",centerZ).putBoolean("offline",true).apply();
                if(!isDestroyed()){minZoom=minZ;maxZoom=maxZ;zoomMask=maskZ;map.lat=centerLat;map.lon=centerLon;map.z=centerZ;setOffline(true);Toast.makeText(this,"Mappa importata · Offline attivo",Toast.LENGTH_LONG).show();}
            });
        });
    }
    protected void onResume(){super.onResume();mapHandler.removeCallbacks(refreshMap);mapHandler.post(refreshMap);}
    protected void onPause(){mapHandler.removeCallbacks(refreshMap);cancelLocate();mapPrefs().edit().putLong("lat",Double.doubleToLongBits(map.lat)).putLong("lon",Double.doubleToLongBits(map.lon)).putInt("zoom",map.z).apply();super.onPause();}
    protected void onSaveInstanceState(Bundle b){b.putDouble("mapLat",map.lat);b.putDouble("mapLon",map.lon);b.putInt("mapZoom",map.z);super.onSaveInstanceState(b);}
    protected void onDestroy(){mapHandler.removeCallbacksAndMessages(null);cancelLocate();map.close();importer.shutdown();super.onDestroy();}
    private final class TileView extends View {
        double lat=45.0677,lon=7.6825;int z=12;volatile int generation;volatile boolean closed,blocked;Location live;
        List<double[]> points=new ArrayList<>(),photos=new ArrayList<>();
        final android.util.LruCache<String,Bitmap> bitmaps=new android.util.LruCache<String,Bitmap>(16*1024*1024){protected int sizeOf(String key,Bitmap b){return b.getByteCount();}};
        final Set<String> loading=new HashSet<>();final Map<String,Long> failures=new HashMap<>();final Set<HttpURLConnection> connections=ConcurrentHashMap.newKeySet();
        final ExecutorService pool=Executors.newFixedThreadPool(3);final Paint paint=new Paint(3);float touchX,touchY;final float scale=getResources().getDisplayMetrics().density;String lastProgress="";final ScaleGestureDetector pinch;
        TileView(){super(MapActivity.this);pinch=new ScaleGestureDetector(MapActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){float value=1;public boolean onScale(ScaleGestureDetector d){value*=d.getScaleFactor();if(value>1.5){zoom(1);value=1;}else if(value<.67){zoom(-1);value=1;}return true;}public void onScaleEnd(ScaleGestureDetector d){value=1;}});setContentDescription("Mappa trascinabile con zoom, traccia e foto geolocalizzate");}
        void reset(){generation++;blocked=false;for(HttpURLConnection c:connections)c.disconnect();connections.clear();loading.clear();bitmaps.evictAll();failures.clear();lastProgress="";invalidate();}
        void close(){closed=true;generation++;for(HttpURLConnection c:connections)c.disconnect();connections.clear();pool.shutdownNow();bitmaps.evictAll();}
        void centerSaved(){if(!points.isEmpty()){double[] p=points.get(points.size()-1);lat=p[0];lon=p[1];}else if(!photos.isEmpty()){double[] p=photos.get(photos.size()-1);lat=p[0];lon=p[1];}invalidate();}
        void zoom(int step){z=offline?TilePolicy.stepZoom(z,step,zoomMask):TilePolicy.zoomWithin(z+step,0,source.maxZoom);if(!blocked)lastError="";invalidate();}
        protected void onDraw(Canvas canvas){
            canvas.drawColor(Ui.dark(MapActivity.this)?Color.rgb(32,47,54):Color.rgb(232,238,234));paint.setStyle(Paint.Style.FILL);paint.setStrokeWidth(dp(1));
            double left=MapMath.x(lon,z)*scale-getWidth()/2.0,top=MapMath.y(lat,z)*scale-getHeight()/2.0,tile=256*scale;int n=1<<z,visible=0,ready=0;
            for(int x=(int)Math.floor(left/tile);x<=Math.floor((left+getWidth())/tile);x++)for(int y=(int)Math.floor(top/tile);y<=Math.floor((top+getHeight())/tile);y++){
                if(y<0||y>=n)continue;visible++;int tx=Math.floorMod(x,n);String key=TilePolicy.key(generation,offline,z,tx,y);float sx=(float)(x*tile-left),sy=(float)(y*tile-top);Bitmap b=bitmaps.get(key);
                if(b!=null){ready++;canvas.drawBitmap(b,null,new RectF(sx,sy,sx+(float)tile,sy+(float)tile),paint);}else{paint.setColor(Ui.dark(MapActivity.this)?0xff34464c:0xffcad7cf);paint.setStyle(Paint.Style.STROKE);canvas.drawRect(sx,sy,sx+(float)tile,sy+(float)tile,paint);paint.setStyle(Paint.Style.FILL);request(key,z,tx,y);}
            }
            paint.setColor(0xff157b5b);paint.setStrokeWidth(dp(4));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);Path path=new Path();boolean segment=false;long session=-1;
            for(double[] p:points){float x=(float)(MapMath.x(p[1],z)*scale-left),y=(float)(MapMath.y(p[0],z)*scale-top);if(p[3]<0){paint.setStyle(Paint.Style.FILL);paint.setColor(0xffffa442);canvas.drawCircle(x,y,dp(5),paint);paint.setColor(0xff157b5b);paint.setStyle(Paint.Style.STROKE);continue;}if(!segment||p[2]==1||(long)p[3]!=session)path.moveTo(x,y);else path.lineTo(x,y);segment=true;session=(long)p[3];}
            paint.setColor(Color.WHITE);paint.setStrokeWidth(dp(6));canvas.drawPath(path,paint);paint.setColor(0xff157b5b);paint.setStrokeWidth(dp(3));canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(0xffa45eee);for(double[] p:photos)canvas.drawCircle((float)(MapMath.x(p[1],z)*scale-left),(float)(MapMath.y(p[0],z)*scale-top),dp(5),paint);
            if(live!=null&&SystemClock.elapsedRealtimeNanos()-live.getElapsedRealtimeNanos()<60_000_000_000L){float px=(float)(MapMath.x(live.getLongitude(),z)*scale-left),py=(float)(MapMath.y(live.getLatitude(),z)*scale-top);paint.setColor(0x302884cf);canvas.drawCircle(px,py,dp(19),paint);paint.setColor(Color.WHITE);canvas.drawCircle(px,py,dp(8),paint);paint.setColor(0xff2682c8);canvas.drawCircle(px,py,dp(5),paint);}
            String progress=generation+":"+ready+":"+visible;if(!progress.equals(lastProgress)){lastProgress=progress;int finalReady=ready,finalVisible=visible,gen=generation;post(()->{if(closed||gen!=generation)return;readyTiles=finalReady;totalTiles=finalVisible;if(finalReady==finalVisible)lastError="";updateNotice();});}
        }
        private void request(String key,int zoom,int x,int y){
            if(closed||(!offline&&blocked)||loading.contains(key)||loading.size()>=18||failures.getOrDefault(key,0L)>System.currentTimeMillis())return;loading.add(key);int gen=generation;boolean local=offline;TileSource selected=source;
            pool.execute(()->{if(closed||gen!=generation){post(()->loading.remove(key));return;}Bitmap bmp=null;String error=null;
                try{if(local){if(!offlineFile().exists())throw new IOException("Nessun MBTiles importato");try(SQLiteDatabase db=SQLiteDatabase.openDatabase(offlineFile().getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor c=db.rawQuery("SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=? LIMIT 1",new String[]{""+zoom,""+x,""+MapMath.tms(zoom,y)})){if(c.moveToFirst()){bmp=decode(c.getBlob(0));if(bmp==null)throw new IOException("Tessera MBTiles non decodificabile");}else throw new IOException("MBTiles: area non coperta a zoom "+zoom);}}
                else{File cache=new File(getCacheDir(),"tiles-v2/"+selected.name());cache.mkdirs();File f=new File(cache,zoom+"_"+x+"_"+y+".png");if(f.exists()){try{bmp=decode(java.nio.file.Files.readAllBytes(f.toPath()));}catch(IOException ignored){}if(!TilePolicy.cacheUsable(f.lastModified(),System.currentTimeMillis(),bmp!=null)){bmp=null;f.delete();}}
                    if(bmp==null){if(blocked)throw new TileTransport.BlockedException();if(closed||gen!=generation)throw new InterruptedIOException("Modalità mappa cambiata");byte[] data=TileTransport.fetch(selected.endpoint(zoom,x,y),connections);bmp=decode(data);if(bmp==null)throw new IOException("Risposta cartografica non riconosciuta come immagine");android.util.AtomicFile atomic=new android.util.AtomicFile(f);FileOutputStream output=atomic.startWrite();try{output.write(data);atomic.finishWrite(output);}catch(IOException e){atomic.failWrite(output);/* Display valid tile even when cache cannot be written. */}}
                }}catch(Exception e){if(e instanceof TileTransport.BlockedException&&gen==generation)blocked=true;error=TileTransport.message(e);}
                Bitmap result=bmp;String err=error;post(()->{loading.remove(key);if(closed||gen!=generation)return;if(result!=null){bitmaps.put(key,result);failures.remove(key);}else{failures.put(key,System.currentTimeMillis()+(local?60000:6000));lastError=err==null?"Tessera non disponibile":err;updateNotice();}invalidate();});
            });
        }
        Bitmap decode(byte[] data){if(data==null||data.length>2*1024*1024)return null;BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);if(o.outWidth<=0||o.outHeight<=0||o.outWidth>1024||o.outHeight>1024)return null;o.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(data,0,data.length,o);}
        public boolean onTouchEvent(MotionEvent e){pinch.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_DOWN){touchX=e.getX();touchY=e.getY();return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){if(!pinch.isInProgress()&&e.getPointerCount()==1){double wx=MapMath.x(lon,z)-(e.getX()-touchX)/scale,wy=MapMath.y(lat,z)-(e.getY()-touchY)/scale;lon=((MapMath.lon(wx,z)+180)%360+360)%360-180;lat=Math.max(-85.05112878,Math.min(85.05112878,MapMath.lat(wy,z)));if(!blocked)lastError="";invalidate();}touchX=e.getX();touchY=e.getY();return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}
        public boolean performClick(){super.performClick();return true;}
    }
}
