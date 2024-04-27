package javaTools.http;

import java.net.URI;

public class HttpTools {
    public static boolean illegalUrl(String url){
        try {
            URI uri = new URI(url);
            uri.toURL();
            return false;
        }catch (Exception e){
            e.printStackTrace();
            return true;
        }
    }
}
