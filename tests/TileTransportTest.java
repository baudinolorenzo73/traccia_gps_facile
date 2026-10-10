import it.lorenzo.tracciagps.TileTransport;
import it.lorenzo.tracciagps.TileSource;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
public final class TileTransportTest {
    private static void rejected(int code,String header)throws Exception{try{TileTransport.validateResponse(code,header);throw new AssertionError("Blocco non rilevato");}catch(TileTransport.BlockedException expected){}}
    public static void main(String[] args)throws Exception{
        TileTransport.validateResponse(200,null);rejected(200,"Access denied");rejected(403,null);rejected(429,null);
        try{TileTransport.validateResponse(503,null);throw new AssertionError();}catch(IOException expected){}
        if(TileSource.from("unknown")!=TileSource.TOPO||TileSource.TOPO.maxZoom!=17)throw new AssertionError();
        try{TileTransport.fetch("http://localhost/tile.png",ConcurrentHashMap.newKeySet());throw new AssertionError();}catch(IOException expected){}
        System.out.println("PASS: blocco HTTP 200/X-Blocked, 403/429, errore server, HTTPS obbligatorio, fonte e zoom.");
        if(args.length==0)return;
        try{byte[] data=TileTransport.fetch(args[0],ConcurrentHashMap.newKeySet());if(args.length>1&&args[1].equals("blocked"))throw new AssertionError("Atteso blocco server");
            if(data.length<8||data[0]!=(byte)137||data[1]!=80||data[2]!=78||data[3]!=71)throw new AssertionError("Risposta non PNG");
            System.out.println("PASS: richiesta live PNG, senza header di blocco ("+data.length+" byte). Verificare anche il contenuto visivo.");
        }catch(TileTransport.BlockedException e){if(args.length<2||!args[1].equals("blocked"))throw e;System.out.println("PASS: blocco live segnalato; immagine di errore rifiutata.");}
    }
}
