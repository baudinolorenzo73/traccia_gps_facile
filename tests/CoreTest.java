import it.lorenzo.tracciagps.*;
public final class CoreTest {
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        check(GpsFilter.valid(45,7,5,1000,50,10000),"fix valido");
        check(!GpsFilter.valid(45,7,Double.NaN,1000,50,10000),"precisione NaN");
        check(!GpsFilter.valid(45,7,-1,1000,50,10000),"precisione negativa");
        check(!GpsFilter.valid(45,7,51,1000,50,10000),"fix impreciso");
        check(!GpsFilter.valid(45,7,5,-1,50,10000),"timestamp futuro");
        check(!GpsFilter.valid(45,7,5,10001,50,10000),"fix vecchio");
        check(!GpsFilter.valid(91,7,5,1000,50,10000),"latitudine invalida");
        check(GpsFilter.jump(1000,5_000_000_000L),"salto GPS");
        check(!GpsFilter.jump(10,5_000_000_000L),"camminata");
        check(GpsFilter.jump(1,0),"tempo duplicato");
        for(int z=0;z<=19;z++)for(double lat:new double[]{-80,-45,0,45,80}){
            check(Math.abs(MapMath.lat(MapMath.y(lat,z),z)-lat)<1e-8,"Mercatore latitudine");
            check(Math.abs(MapMath.lon(MapMath.x(7.5,z),z)-7.5)<1e-8,"Mercatore longitudine");
            int y=(1<<z)-1;check(MapMath.tms(z,y)==0,"TMS sud");check(MapMath.tms(z,0)==y,"TMS nord");
        }
        check(Double.isFinite(MapMath.y(90,19)),"polo limitato");
        System.out.println("PASS: filtri GPS, salti, proiezione Mercatore e conversione MBTiles TMS.");
    }
}
