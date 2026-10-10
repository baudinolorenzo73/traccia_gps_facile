package it.lorenzo.tracciagps;
/** Public raster providers, independent caches and supported zooms. */
public enum TileSource {
    TOPO("OpenTopoMap", "https://tile.opentopomap.org/",17,"© OpenStreetMap · SRTM\n© OpenTopoMap (CC-BY-SA)","https://opentopomap.org/about"),
    OSM("OpenStreetMap", "https://tile.openstreetmap.org/",19,"© OpenStreetMap contributors","https://www.openstreetmap.org/copyright");
    public final String label,base,credit,license;public final int maxZoom;
    TileSource(String label,String base,int maxZoom,String credit,String license){this.label=label;this.base=base;this.maxZoom=maxZoom;this.credit=credit;this.license=license;}
    public String endpoint(int z,int x,int y){return base+z+"/"+x+"/"+y+".png";}
    public static TileSource from(String value){try{return valueOf(value);}catch(Exception e){return TOPO;}}
}
