import it.lorenzo.tracciagps.UpdateRules;
public final class UpdateRulesTest {
    private static void check(boolean result,String label){if(!result)throw new AssertionError(label);}
    private static void reject(String url){try{UpdateRules.https(url);throw new AssertionError("URL accettato: "+url);}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        reject("http://example.org/update.json");reject("file:///tmp/update.json");reject("https://user:password@example.org/update.json");reject("https://example.org/update.json#fragment");reject("https:///update.json");
        check(UpdateRules.resolve("https://example.org/app/update.json","Traccia-GPS.apk").equals("https://example.org/app/Traccia-GPS.apk"),"APK relativo");
        check(UpdateRules.resolve("https://example.org/app/update.json","https://cdn.example.org/app.apk").equals("https://cdn.example.org/app.apk"),"CDN HTTPS");
        try{UpdateRules.resolve("https://example.org/update.json","http://example.org/app.apk");throw new AssertionError("downgrade HTTPS");}catch(IllegalArgumentException expected){}
        check(UpdateRules.sha("0123456789abcdef".repeat(4)),"SHA valido");check(!UpdateRules.sha("a".repeat(63)),"SHA incompleto");check(!UpdateRules.sha("g".repeat(64)),"SHA non esadecimale");
        check(UpdateRules.newer(3,2),"aggiornamento");check(!UpdateRules.newer(2,2),"stessa versione");check(!UpdateRules.newer(1,2),"downgrade");check(!UpdateRules.newer(Long.MAX_VALUE,2),"overflow versione");
        System.out.println("PASS: URL HTTPS, redirect/destinazioni, SHA-256 e confronto versioni.");
    }
}
