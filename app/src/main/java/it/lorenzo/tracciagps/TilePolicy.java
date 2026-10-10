package it.lorenzo.tracciagps;
/** Cache validity must include decode success, never just file age. */
public final class TilePolicy {
    private TilePolicy(){}
    public static boolean cacheUsable(long modified,long now,boolean decoded){long age=now-modified;return decoded&&age>=0&&age<7L*86400000;}
    public static int zoomWithin(int value,int min,int max){return Math.max(min,Math.min(max,value));}
    public static int stepZoom(int current,int step,int mask){for(int z=current+step;z>=0&&z<=19;z+=step){if((mask&(1<<z))!=0)return z;}return current;}
    public static int closestZoom(int value,int mask){int best=0,distance=100;for(int z=0;z<=19;z++)if((mask&(1<<z))!=0&&Math.abs(z-value)<distance){distance=Math.abs(z-value);best=z;}return best;}
    public static String key(int generation,boolean offline,int z,int x,int y){return generation+":"+(offline?"off":"on")+":"+z+":"+x+":"+y;}
}
