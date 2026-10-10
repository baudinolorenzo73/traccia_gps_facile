package it.lorenzo.tracciagps;
import java.net.URI;
public final class UpdateRules {
    private UpdateRules(){}
    public static String https(String url){
        URI uri=URI.create(url.trim());if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("Serve un indirizzo HTTPS pubblico, senza credenziali o frammenti");return uri.toASCIIString();
    }
    public static String resolve(String base,String target){return https(URI.create(https(base)).resolve(target).toString());}
    public static boolean sha(String value){return value!=null&&value.matches("(?i)[0-9a-f]{64}");}
    public static boolean newer(long offered,long installed){return offered>installed&&offered<=Integer.MAX_VALUE;}
}
