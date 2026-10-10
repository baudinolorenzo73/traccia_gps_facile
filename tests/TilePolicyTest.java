import it.lorenzo.tracciagps.TilePolicy;
public final class TilePolicyTest {
    private static void check(boolean result,String name){if(!result)throw new AssertionError(name);}
    public static void main(String[] args){
        long now=1_800_000_000_000L;
        check(TilePolicy.cacheUsable(now-1000,now,true),"cache valida");
        check(!TilePolicy.cacheUsable(now-1000,now,false),"cache corrotta deve riscaricare");
        check(!TilePolicy.cacheUsable(now-8L*86400000,now,true),"cache scaduta");
        check(!TilePolicy.cacheUsable(now+1000,now,true),"cache timestamp futuro");
        check(TilePolicy.zoomWithin(18,10,14)==14,"limite MBTiles superiore");
        check(TilePolicy.zoomWithin(6,10,14)==10,"limite MBTiles inferiore");
        check(!TilePolicy.key(1,false,12,2135,1472).equals(TilePolicy.key(2,false,12,2135,1472)),"richieste vecchie non bloccano nuova generazione");
        check(!TilePolicy.key(1,false,12,2135,1472).equals(TilePolicy.key(1,true,12,2135,1472)),"separazione online offline");
        int mask=(1<<10)|(1<<14);check(TilePolicy.stepZoom(10,1,mask)==14,"salta livelli mancanti");check(TilePolicy.stepZoom(14,-1,mask)==10,"zoom precedente presente");check(TilePolicy.closestZoom(13,mask)==14,"centro su zoom presente");
        System.out.println("PASS: cache corrotta/scaduta, zoom offline e richieste indipendenti per generazione.");
    }
}
