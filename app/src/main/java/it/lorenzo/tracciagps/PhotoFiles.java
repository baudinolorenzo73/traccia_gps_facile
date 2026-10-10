package it.lorenzo.tracciagps;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.json.JSONObject;
public final class PhotoFiles {
    private PhotoFiles(){}
    public static void archive(Context context,Store store,OutputStream output) throws Exception {
        try(ZipOutputStream zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("tracce.gpx"));Writer writer=new OutputStreamWriter(zip,StandardCharsets.UTF_8);store.export(writer);writer.flush();zip.closeEntry();
            File dir=new File(context.getFilesDir(),"photos");File[] photos=dir.listFiles((d,n)->n.endsWith(".jpg"));if(photos==null)photos=new File[0];
            zip.putNextEntry(new ZipEntry("foto.gpx"));writer=new OutputStreamWriter(zip,StandardCharsets.UTF_8);Gpx.header(writer);
            for(File f:photos){File meta=new File(dir,f.getName()+".json");if(meta.exists()){JSONObject j=new JSONObject(new String(java.nio.file.Files.readAllBytes(meta.toPath()),StandardCharsets.UTF_8));writer.write("<wpt lat=\""+j.getDouble("lat")+"\" lon=\""+j.getDouble("lon")+"\"><time>"+java.time.Instant.ofEpochMilli(j.getLong("fixTime"))+"</time><name>"+Gpx.escape(f.getName())+"</name><desc>Posizione GPS rilevata dopo lo scatto; precisione "+j.getDouble("accuracy")+" m</desc><link href=\"foto/"+Gpx.escape(f.getName())+"\"><text>Foto</text><type>image/jpeg</type></link></wpt>\n");}}
            writer.write("</gpx>\n");writer.flush();zip.closeEntry();
            byte[] buffer=new byte[65536];for(File f:photos){for(File file:new File[]{f,new File(dir,f.getName()+".json")}){if(!file.exists())continue;zip.putNextEntry(new ZipEntry("foto/"+file.getName()));try(InputStream in=new FileInputStream(file)){int n;while((n=in.read(buffer))!=-1)zip.write(buffer,0,n);}zip.closeEntry();}}
        }
    }
}
