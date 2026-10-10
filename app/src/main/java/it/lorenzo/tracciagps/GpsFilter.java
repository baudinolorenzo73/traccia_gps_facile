package it.lorenzo.tracciagps;
/** Pure validation, shared by track, waypoint and photo acquisition. */
public final class GpsFilter {
    private GpsFilter(){}
    public static boolean valid(double lat,double lon,double accuracy,long age,double limit,long maxAge){
        return Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180&&Double.isFinite(accuracy)&&accuracy>=0&&accuracy<=limit&&age>=0&&age<=maxAge;
    }
    public static boolean jump(double distance,long nanos){return nanos<=0||!Double.isFinite(distance)||distance<0||distance/(nanos/1e9)>15;}
}
