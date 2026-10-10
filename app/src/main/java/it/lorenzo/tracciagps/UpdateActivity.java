package it.lorenzo.tracciagps;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

public final class UpdateActivity extends Activity {
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean busy=new AtomicBoolean(false);
    private static volatile String outcome="";
    private static volatile Offer offer;
    private static final long MAX_APK=150L*1024*1024;
    private TextView info,status;private Button check,download,install,local,configure;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,700);}};
    private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("updates",0);}
    private static long code(PackageInfo p){return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
    private static PackageInfo installed(Context c) throws PackageManager.NameNotFoundException {return c.getPackageManager().getPackageInfo(c.getPackageName(),Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);}
    public static String version(Context c){try{PackageInfo p=installed(c);return p.versionName+" · build "+code(p);}catch(Exception e){return "Versione non disponibile";}}
    public static String summary(Context c){try{if(offer!=null&&UpdateRules.newer(offer.code,code(installed(c))))return "Aggiornamento disponibile: "+offer.name;}catch(Exception ignored){}return busy.get()?"Operazione aggiornamento in corso…":"Versione installata: "+version(c);}
    public static void autoCheck(Context c){if(offer==null){String cached=prefs(c).getString("offer","");if(!cached.isEmpty())try{offer=Offer.parse(cached,prefs(c).getString("url",""));}catch(Exception ignored){}}String url=prefs(c).getString("url","");if(!url.isEmpty()&&prefs(c).getBoolean("auto",true)&&System.currentTimeMillis()-prefs(c).getLong("lastAttempt",0)>86400000L)check(c.getApplicationContext());}
    public void onCreate(Bundle b){setTheme(getSharedPreferences("ui",0).getBoolean("dark",true)?R.style.AppThemeDark:R.style.AppTheme);super.onCreate(b);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.bg(this));LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(Ui.dp(this,20),Ui.dp(this,20),Ui.dp(this,20),Ui.dp(this,24));scroll.addView(root);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        TextView title=Ui.label(this,"Versione e aggiornamenti",25,true);root.addView(title);info=Ui.label(this,"",17,false);root.addView(info);status=Ui.label(this,"",14,false);status.setTextColor(Ui.muted(this));status.setPadding(0,Ui.dp(this,16),0,Ui.dp(this,16));root.addView(status);
        configure=button(root,"Configura fonte aggiornamenti",()->configure());
        CheckBox automatic=new CheckBox(this);automatic.setText("Controlla all’apertura, al massimo una volta al giorno");automatic.setChecked(prefs(this).getBoolean("auto",true));automatic.setOnCheckedChangeListener((v,value)->prefs(this).edit().putBoolean("auto",value).apply());root.addView(automatic);
        check=button(root,"Controlla aggiornamenti",()->{if(prefs(this).getString("url","").isEmpty())configure();else check(getApplicationContext());});
        download=button(root,"Scarica aggiornamento",()->download());
        install=button(root,"Installa aggiornamento scaricato",()->install());
        local=button(root,"Installa APK da file",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");try{startActivityForResult(i,71);}catch(ActivityNotFoundException e){alert("Gestore file non disponibile");}});
        TextView help=Ui.label(this,"",13,false);help.setTextColor(Ui.muted(this));help.setPadding(0,Ui.dp(this,16),0,0);help.setText("Android chiederà l’autorizzazione a installare da questa app e la conferma finale. Prima di installare ferma la traccia. Gli aggiornamenti devono avere lo stesso identificativo e la stessa firma della beta installata; i dati restano conservati. Per la prima installazione apri l’APK dal telefono. Il controllo automatico non scarica né installa nulla.");root.addView(help);
        if(offer==null){String json=prefs(this).getString("offer","");if(!json.isEmpty())try{offer=Offer.parse(json,prefs(this).getString("url",""));}catch(Exception ignored){}}
        render();
    }
    private Button button(LinearLayout root,String text,Runnable action){Button b=Ui.action(this,text,text.startsWith("Scarica")||text.startsWith("Installa aggiornamento"),action);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=Ui.dp(this,8);root.addView(b,params);return b;}
    private void alert(String message){new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK",null).show();}
    private void render(){info.setText("Installata: "+version(this));String url=prefs(this).getString("url","");String message=outcome;
        if(message.isEmpty())message=url.isEmpty()?"Fonte online da configurare. Puoi già installare un APK compatibile scelto dal telefono.":"Premi Controlla aggiornamenti.";
        status.setText(message);boolean active=busy.get();check.setEnabled(!active);configure.setEnabled(!active);local.setEnabled(!active);boolean ready=false;try{ready=UpdateRules.newer(prefs(this).getLong("readyVersion",0),code(installed(this)));}catch(Exception ignored){}install.setEnabled(!active&&ready&&new File(getFilesDir(),"update.apk").exists());
        boolean newer=false;try{newer=offer!=null&&UpdateRules.newer(offer.code,code(installed(this)));}catch(Exception ignored){}download.setEnabled(!active&&newer);
    }
    private void configure(){if(busy.get())return;EditText edit=new EditText(this);edit.setSingleLine(true);edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);edit.setHint("https://…/update.json");edit.setText(prefs(this).getString("url",""));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Fonte aggiornamenti HTTPS").setMessage("Inserisci l’indirizzo pubblico del file update.json pubblicato insieme agli APK. Lascia vuoto per disattivare il controllo online.").setView(edit).setPositiveButton("Salva",null).setNegativeButton("Annulla",null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{String url=edit.getText().toString().trim();if(!url.isEmpty())url=UpdateRules.https(url);prefs(this).edit().putString("url",url).remove("offer").remove("lastAttempt").apply();offer=null;outcome=url.isEmpty()?"Controllo online disattivato.":"Fonte salvata. Premi Controlla aggiornamenti.";dialog.dismiss();render();}catch(Exception e){edit.setError(e.getMessage());}}));dialog.show();
    }
    private static final class Offer {
        long code;String name,url,sha,notes,raw;
        static Offer parse(String json,String base) throws Exception {JSONObject j=new JSONObject(json);Offer o=new Offer();o.code=j.getLong("versionCode");if(o.code<1||o.code>Integer.MAX_VALUE)throw new IOException("versionCode non valido");o.name=j.getString("versionName");o.url=UpdateRules.resolve(base,j.getString("apkUrl"));o.sha=j.getString("sha256");if(!UpdateRules.sha(o.sha))throw new IOException("SHA-256 mancante o non valido");o.notes=j.optString("notes","");o.raw=json;return o;}
    }
    private static void check(Context c){String url=prefs(c).getString("url","");if(url.isEmpty()||!busy.compareAndSet(false,true))return;outcome="Controllo versione online…";prefs(c).edit().putLong("lastAttempt",System.currentTimeMillis()).apply();
        worker.execute(()->{try{ByteArrayOutputStream out=new ByteArrayOutputStream();try(Net net=open(url)){copy(net.in,out,65536);}Offer next=Offer.parse(out.toString("UTF-8"),url);offer=next;prefs(c).edit().putString("offer",next.raw).putLong("lastSuccess",System.currentTimeMillis()).apply();outcome=UpdateRules.newer(next.code,code(installed(c)))?"Disponibile "+next.name+" · build "+next.code+"\n"+next.notes:"Hai già la versione più recente pubblicata ("+next.name+").";}catch(Exception e){offer=null;prefs(c).edit().remove("offer").apply();outcome="Controllo non riuscito: "+friendly(e)+". La versione installata continua a funzionare.";}finally{busy.set(false);}});
    }
    private void download(){Offer selected=offer;if(selected==null||!busy.compareAndSet(false,true))return;outcome="Scaricamento aggiornamento…";Context c=getApplicationContext();
        worker.execute(()->{File temp=new File(c.getFilesDir(),"update.part");try(Net net=open(selected.url);FileOutputStream out=new FileOutputStream(temp)){copy(net.in,out,MAX_APK);out.getFD().sync();String hash=hash(temp);if(!hash.equalsIgnoreCase(selected.sha))throw new IOException("SHA-256 differente: download rifiutato");PackageInfo apk=validate(c,temp);if(code(apk)!=selected.code||!Objects.equals(apk.versionName,selected.name))throw new IOException("La versione dell’APK non corrisponde al manifest online");if(!temp.renameTo(new File(c.getFilesDir(),"update.apk")))throw new IOException("Salvataggio APK fallito");prefs(c).edit().putLong("readyVersion",code(apk)).apply();outcome="Scaricato e controllato: "+apk.versionName+". Premi Installa aggiornamento scaricato.";}catch(Exception e){temp.delete();outcome="Scaricamento non riuscito: "+friendly(e);}finally{busy.set(false);}});
    }
    private static PackageInfo validate(Context c,File file) throws Exception {
        int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;PackageInfo apk=c.getPackageManager().getPackageArchiveInfo(file.getPath(),flags),current=installed(c);
        if(apk==null)throw new IOException("APK non leggibile");if(!c.getPackageName().equals(apk.packageName))throw new IOException("APK di un’app diversa");if(!UpdateRules.newer(code(apk),code(current)))throw new IOException("Serve una versione più recente di quella installata");if(apk.applicationInfo==null||apk.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)throw new IOException("APK incompatibile con questo Android");
        if(!signatures(apk).equals(signatures(current)))throw new IOException("Firma diversa: aggiornamento rifiutato");return apk;
    }
    private static Set<String> signatures(PackageInfo p) throws Exception {Signature[] values=Build.VERSION.SDK_INT>=28?(p.signingInfo==null?null:p.signingInfo.getApkContentsSigners()):p.signatures;if(values==null||values.length==0)throw new IOException("Firma APK non disponibile");Set<String> result=new HashSet<>();for(Signature s:values)result.add(hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));return result;}
    private void install(){if(busy.get())return;if(TrackingService.running){alert("Ferma la traccia prima di installare l’aggiornamento.");return;}
        try{PackageInfo apk=validate(this,new File(getFilesDir(),"update.apk"));if(!getPackageManager().canRequestPackageInstalls()){new AlertDialog.Builder(this).setTitle("Autorizza l’installazione").setMessage("Abilita Consenti da questa fonte nelle impostazioni Android. Poi torna e premi nuovamente Installa aggiornamento scaricato.").setPositiveButton("Apri impostazioni",(d,w)->{try{startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){alert("Apri manualmente Impostazioni → Installa app sconosciute.");}}).setNegativeButton("Annulla",null).show();return;}
            Uri uri=UpdateProvider.uri(this);Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("Aggiornamento APK",uri));startActivity(i);outcome="Richiesta installazione "+apk.versionName+" inviata ad Android. Conferma nella schermata di sistema.";
        }catch(Exception e){alert("Installazione non avviata: "+friendly(e));}
    }
    protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);if(r!=71||result!=RESULT_OK||data==null||data.getData()==null||!busy.compareAndSet(false,true))return;Uri uri=data.getData();Context c=getApplicationContext();outcome="Copia e controllo APK scelto…";worker.execute(()->{File temp=new File(c.getFilesDir(),"update.part");try(InputStream in=c.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(temp)){if(in==null)throw new IOException("File non disponibile");copy(in,out,MAX_APK);out.getFD().sync();PackageInfo apk=validate(c,temp);if(!temp.renameTo(new File(c.getFilesDir(),"update.apk")))throw new IOException("Salvataggio APK fallito");prefs(c).edit().putLong("readyVersion",code(apk)).apply();outcome="APK compatibile: "+apk.versionName+". Premi Installa aggiornamento scaricato.";}catch(Exception e){temp.delete();outcome="APK rifiutato: "+friendly(e);}finally{busy.set(false);}});}
    private static void copy(InputStream in,OutputStream out,long limit) throws IOException {byte[] buffer=new byte[65536];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>limit)throw new IOException("File oltre il limite consentito");out.write(buffer,0,n);}if(total==0)throw new IOException("File vuoto");}
    private static String hex(byte[] data){StringBuilder s=new StringBuilder();for(byte b:data)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private static String hash(File f) throws Exception {MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}return hex(d.digest());}
    private static String friendly(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private static final class Net implements AutoCloseable {final HttpURLConnection connection;final InputStream in;Net(HttpURLConnection c,InputStream i){connection=c;in=i;}public void close() throws IOException {try{in.close();}finally{connection.disconnect();}}}
    private static Net open(String initial) throws Exception {String url=UpdateRules.https(initial);for(int redirects=0;redirects<=5;redirects++){HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","TracciaGPSOffline-Updater/2.2");int response;try{response=c.getResponseCode();if(response>=300&&response<=399){String next=c.getHeaderField("Location");c.disconnect();if(next==null)throw new IOException("Redirect senza destinazione");url=UpdateRules.resolve(url,next);continue;}if(response!=200)throw new IOException("Server HTTP "+response);return new Net(c,c.getInputStream());}catch(Exception e){c.disconnect();throw e;}}throw new IOException("Troppi redirect");}
    protected void onResume(){super.onResume();handler.post(refresh);}
    protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
