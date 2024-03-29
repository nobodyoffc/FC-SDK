//package initial;
//
//import FEIP.feipData.Service;
//import appTools.Inputer;
//import com.google.gson.Gson;
//import constants.Strings;
//import data.OpenDriveParams;
//import database.redisTools.GetJedis;
//import javaTools.JsonTools;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import redis.clients.jedis.Jedis;
//import redis.clients.jedis.JedisPool;
//import javax.servlet.ServletConfig;
//import javax.servlet.http.HttpServlet;
//import java.io.Serial;
//
//public class Initiator extends HttpServlet {
//    @Serial
//    private static final long serialVersionUID = 1L;
//    private static final Logger log = LoggerFactory.getLogger(Initiator.class);
//    public static JedisPool jedisPool;
//    public static ConfigOpenDrive configOpenDrive;
//    public static  String serviceIdHead;
//    public static Service service;
//    public static OpenDriveParams params;
//    public static LocalDateBase localDateBase;
//    public static String UserFileName = "User.json";
//    public static byte[] apiPassword;
//    public static byte[] apiSessionKey;
//    public static String apiSessionName;
//
//    /*
//    1. The data needed when this running: order tx(cash). It can be from: 1)RPC,APIP, ThirdParty
//    2. Data storage: 1) config.json for initial data, 2)redis for running data, 3)file for backup, 4)file for user's data.
//     */
//
//    @Override
//    public void init(ServletConfig config) {
//        log.debug("init starting...");
//
//        jedisPool = GetJedis.createJedisPool();
//
//        Gson gson = new Gson();
//        configOpenDrive = ConfigOpenDrive.readConfigJsonFromFile();
//        if (ConfigOpenDrive.isBadConfig(configOpenDrive)) return;
//
//        byte[] passwordBytes = Inputer.getPasswordStrFromEnvironment();
//        if(passwordBytes==null)return;
//
//        isGoodApiProvider = checkApiProvider(configOpenDrive.getApiProvider(),configOpenDrive.getApiAccount(),passwordBytes);
//        if(!isGoodApiProvider){
//            log.debug("Failed to get API provider. Check the API provider.");
//            return;
//        }
//
//        service = getDriveService(configOpenDrive.getOpenDriveSid(),configOpenDrive.getApiProvider(),configOpenDrive.getApiAccount());
//        if(service == null){
//            log.debug("Failed to get your OpenDrive service on chain.");
//            return;
//        }
//
//        serviceIdHead = service.getSid().substring(0,6);
//
//        params = gson.fromJson(gson.toJson(service.getParams()),OpenDriveParams.class);
//        if(params==null){
//            log.debug("Failed to get the parameters of the OpenDrive service {} on chain.",service.getSid());
//            return;
//        }
//
//        try(Jedis jedis = jedisPool.getResource()) {
//        }catch (Exception e){
//            log.error("Get service or nPrice from redis wrong.");
//            jedisPool.close();
//            return;
//        }
//    }
//
////        String rpcIp = configOpenDrive.getRpcIp();
////        int rpcPort = configOpenDrive.getRpcPort();
////        String rpcUser = configOpenDrive.getRpcUser();
////        String rpcPassword = configOpenDrive.getRpcPassword();
////
////
////        log.debug("APIP server initiated successfully.");
//
//
//
//
//    @Override
//    public void destroy(){
//        log.debug("Destroy APIP server...");
//        jedisPool.close();
//        try {
//            jedisPool.close();
//        } catch (Exception e) {
//            log.debug("Shutdown NewEsClient wrong.");
//        }
//
//        log.debug("APIP server is stopped.");
//    }
//}
