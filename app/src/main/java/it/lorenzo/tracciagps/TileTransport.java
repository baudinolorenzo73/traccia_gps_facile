package it.lorenzo.tracciagps;
import java.io.*;
import java.net.*;
import java.util.Set;
/** Same bounded HTTPS transport used by map and diagnostic screen. */
public final class TileTransport {
    private TileTransport(){}
    public static final class BlockedException extends IOException { public BlockedException(){super("Accesso cartografico bloccato dal server · Carte → Fonte online o usa Offline");} }
    public static void validateResponse(int code,String blocked) throws IOException {
        if((blocked!=null&&!blocked.trim().isEmpty())||code==403||code==429)throw new BlockedException();
        if(code!=200)throw new IOException("Server cartografico HTTP "+code);
    }
    public static byte[] fetch(String endpoint,Set<HttpURLConnection> active) throws IOException {
        String url=endpoint;
        for(int redirects=0;redirects<=4;redirects++){
            URL target=new URL(url);if(!"https".equals(target.getProtocol()))throw new IOException("Cartografia: indirizzo non HTTPS");
            HttpURLConnection c=(HttpURLConnection)target.openConnection();active.add(c);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","TracciaGPSOffline/2.2 (it.lorenzo.tracciagps.beta2)");c.setConnectTimeout(15000);c.setReadTimeout(20000);
            try{int response=c.getResponseCode();if(response==301||response==302||response==303||response==307||response==308){String location=c.getHeaderField("Location");if(location==null)throw new IOException("Redirect senza indirizzo");url=new URL(target,location).toString();continue;}validateResponse(response,c.getHeaderField("X-Blocked"));
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Richiesta annullata");if(out.size()+n>2*1024*1024)throw new IOException("Tessera oltre 2 MB");out.write(buf,0,n);}if(out.size()==0)throw new IOException("Tessera vuota");return out.toByteArray();}
            }finally{c.disconnect();active.remove(c);}
        }
        throw new IOException("Troppi redirect del server cartografico");
    }
    public static String message(Exception e){if(e instanceof java.net.SocketTimeoutException)return "Connessione lenta: tempo di attesa superato";if(e instanceof java.net.UnknownHostException)return "Nome del server non risolto: controlla rete, DNS o VPN";if(e instanceof javax.net.ssl.SSLException)return "Connessione HTTPS rifiutata: controlla data del telefono, VPN o certificati";return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
}
