package it.lorenzo.tracciagps;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
/** Read-only, grant-only URI for the single staged APK. */
public final class UpdateProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    public static Uri uri(Context c){return Uri.parse("content://"+c.getPackageName()+".updates/update.apk");}
    private File file(Uri u) throws FileNotFoundException {if(!"/update.apk".equals(u.getPath()))throw new FileNotFoundException();return new File(getContext().getFilesDir(),"update.apk");}
    public ParcelFileDescriptor openFile(Uri u,String mode) throws FileNotFoundException {if(!"r".equals(mode))throw new FileNotFoundException("Sola lettura");return ParcelFileDescriptor.open(file(u),ParcelFileDescriptor.MODE_READ_ONLY);}
    public String getType(Uri u){return "application/vnd.android.package-archive";}
    public Cursor query(Uri u,String[] projection,String s,String[] args,String sort){try{File f=file(u);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(cols[i])?"Traccia-GPS-aggiornamento.apk":OpenableColumns.SIZE.equals(cols[i])?f.length():null;c.addRow(row);return c;}catch(IOException e){return null;}}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
