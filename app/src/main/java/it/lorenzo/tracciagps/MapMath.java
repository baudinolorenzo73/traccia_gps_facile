package it.lorenzo.tracciagps;
public final class MapMath {
    private MapMath(){}
    public static double x(double lon,int zoom){return (lon+180)/360*(1<<zoom)*256;}
    public static double y(double lat,int zoom){double s=Math.sin(Math.toRadians(Math.max(-85.05112878,Math.min(85.05112878,lat))));return (.5-Math.log((1+s)/(1-s))/(4*Math.PI))*(1<<zoom)*256;}
    public static double lat(double y,int zoom){return Math.toDegrees(Math.atan(Math.sinh(Math.PI-2*Math.PI*y/((1<<zoom)*256))));}
    public static double lon(double x,int zoom){return x/((1<<zoom)*256)*360-180;}
    public static long tms(int zoom,int y){return (1L<<zoom)-1-y;}
}
