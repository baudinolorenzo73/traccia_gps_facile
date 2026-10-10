package it.lorenzo.tracciagps;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Only immediate JPEG children of the private photos directory are exposed. */
public final class PhotoProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    public static Uri uri(Context c,String name){return new Uri.Builder().scheme("content").authority(c.getPackageName()+".photos").appendPath(name).build();}
    private File file(Uri u) throws FileNotFoundException {
        String name=u.getLastPathSegment();
        if(u.getPathSegments().size()!=1 || name==null || !name.matches("photo_[0-9a-f-]+\\.jpg"))throw new FileNotFoundException("Foto non valida");
        return new File(new File(getContext().getFilesDir(),"photos"),name);
    }
    public ParcelFileDescriptor openFile(Uri u,String mode) throws FileNotFoundException {
        if(!mode.equals("r")&&!mode.equals("w")&&!mode.equals("rw")&&!mode.equals("rwt"))throw new FileNotFoundException("Modalità non valida");
        return ParcelFileDescriptor.open(file(u),ParcelFileDescriptor.parseMode(mode));
    }
    public String getType(Uri u){return "image/jpeg";}
    public Cursor query(Uri u,String[] projection,String selection,String[] args,String sort){
        try{File f=file(u);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++)row[i]=cols[i].equals(OpenableColumns.DISPLAY_NAME)?f.getName():cols[i].equals(OpenableColumns.SIZE)?f.length():null;c.addRow(row);return c;}catch(IOException e){return null;}
    }
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
