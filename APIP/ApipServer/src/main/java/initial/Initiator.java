package initial;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import config.*;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import javaTools.JsonTools;
import nasa.NaSaRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Settings;
import startAPIP.ApipManagerSettings;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import java.io.File;
import java.io.IOException;
import java.io.Serial;

import static constants.FieldNames.FORBID_FREE_API;
import static constants.Strings.*;
import static server.Settings.addSidBriefToName;


public class Initiator extends HttpServlet {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(Initiator.class);
    public static final ApiType apiType = ApiType.APIP;
    public static String dataPath;
    private ApiAccount esAccount;
    public static String sid;
    public static ElasticsearchClient esClient;
    public static NaSaRpcClient naSaRpcClient;
    public static JedisPool jedisPool;
//    public static Boolean forbidFreeApi;
//    protected static Long windowTime;


    @Override
    public void destroy(){
        log.debug("Destroy APIP server...");
        jedisPool.close();
        esAccount.closeEs();
        log.debug("APIP server is destroyed.");
    }
    @Override
    public void init(ServletConfig config) {
        log.debug("initiate APIP web server...");
        String configFileName = apiType.name()+"_"+CONFIG+DOT_JSON;

        WebServerConfig webServerConfig;
        Configure configure;
        ApipManagerSettings settings;
        try {
            webServerConfig = JsonTools.readJsonFromFile(configFileName,WebServerConfig.class);
            configure = JsonTools.readJsonFromFile(webServerConfig.getConfigPath(),Configure.class);
            settings = JsonTools.readJsonFromFile(webServerConfig.getSettingPath(), ApipManagerSettings.class);
            dataPath = webServerConfig.getDataPath();
            sid = webServerConfig.getSid();
        } catch (IOException e) {
            log.error("Failed to read the config file of "+configFileName+".");
            return;
        }

        ApiAccount redisAccount = configure.getApiAccountMap().get(settings.getRedisAccountId());
        jedisPool = redisAccount.connectRedis();

        ApiAccount nasaAccount = configure.getApiAccountMap().get(settings.getNasaAccountId());
        naSaRpcClient = (NaSaRpcClient) nasaAccount.getClient();

        byte[] symKey;
        String symKeyCipher;
        try(Jedis jedis = jedisPool.getResource()){
            symKeyCipher = jedis.hget(addSidBriefToName(webServerConfig.getSid(), WEB_PARAMS),SYM_KEY_CIPHER);
        }
        CryptoDataByte result = new Decryptor().decryptJsonByPassword(symKeyCipher, configure.getNonce().toCharArray());
        if(result.getCode()!=0){
            System.out.println("Failed to decrypt symKey for web server:"+result.getMessage());
            return;
        }
        symKey = result.getData();

        esAccount = configure.getApiAccountMap().get(settings.getEsAccountId());
        ApiProvider esProvider = configure.getApiProviderMap().get(esAccount.getProviderId());
        esClient = (ElasticsearchClient)esAccount.connectApi(esProvider,symKey);

        log.debug("APIP server initiated successfully.");

        /*
    private long windowTime;
    private long price;

    private int nPrice;
    private boolean isPricePerRequest;
    private long balance;
    private long bestHeight;
         */
    }

//    public static void freshWebParams(WebServerConfig webServerConfig, Jedis jedis) {
//        try {
//            forbidFreeApi = Boolean.parseBoolean(jedis.hget(addSidBriefToName(webServerConfig.getSid(), WEB_PARAMS), FORBID_FREE_API));
//            windowTime = Long.parseLong(jedis.hget(addSidBriefToName(webServerConfig.getSid(), WEB_PARAMS), WINDOW_TIME));
//        }catch (Exception ignore){};
//        if(forbidFreeApi==null)forbidFreeApi=false;
//        if(windowTime==null)windowTime = Settings.DEFAULT_WINDOW_TIME;
//    }
}
