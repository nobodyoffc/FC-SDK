import clients.ApiUrl;
import co.elastic.clients.json.JsonpUtils;
import com.google.gson.Gson;
import javaTools.JsonTools;
import org.checkerframework.checker.units.qual.A;

public class Test {
    public static void main(String[] args) {
        String urlHead = "http://host:80/apip/";
        String urlTailPath = "/apip0/v1/";
        String api = "signIn";
        ApiUrl apiUrl = new ApiUrl(urlHead,urlTailPath,api,null,false,null);

        JsonTools.gsonPrint(apiUrl);
    }
}
