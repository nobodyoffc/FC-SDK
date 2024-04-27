package clients.redisClient;

import crypto.cryptoTools.KeyTools;
import redis.clients.jedis.Jedis;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class RedisTools {
    public static long readHashLong(Jedis jedis, String key, String filed) {
        long var = 0;
        String varStr;
        try {
            varStr = jedis.hget(key, filed);
        } catch (Exception e) {
            varStr = null;
        }
        if (varStr != null) {
            var = Long.parseLong(varStr);
        }
        return var;
    }

    public static void setNumber(String key, String field, BufferedReader br, Jedis jedis) throws IOException {
        while (true) {
            String value = jedis.hget(key, field);
            System.out.println("The " + field + " is " + value + ". Input to set it. Enter to ignore it:");
            String input = br.readLine();
            if ("".equals(input)) return;
            try {
                Double.parseDouble(input);
            } catch (Exception e) {
                System.out.println("Input a number please. Try again. Enter to ignore.");
                continue;
            }
            jedis.hset(key, field, input);
            return;
        }

    }
    public static double readHashDouble(Jedis jedis, String key, String filed) {

        double var = 0;
        String varStr;
        try {
            varStr = jedis.hget(key, filed);
        } catch (Exception e) {
            varStr = null;
        }
        if (varStr != null) {
            var = Double.parseDouble(varStr);
        }
        return var;
    }

    public static long readLong(String key) {

        long var = 0;
        String varStr;
        try (Jedis jedis = new Jedis()) {
            varStr = jedis.get(key);
        } catch (Exception e) {
            varStr = null;
        }
        if (varStr != null) {
            var = Long.parseLong(varStr);
        }
        return var;
    }

    public static void setFid(String key, String field, BufferedReader br, Jedis jedis) throws IOException {
        while (true) {
            String value = jedis.hget(key, field);
            System.out.println("The " + field + " is " + value + ". Input to set it. Enter to ignore it:");
            String input = br.readLine();
            if ("".equals(input)) return;
            if (KeyTools.isValidFchAddr(input)) {
                jedis.hset(key, field, input);
                return;
            } else {
                System.out.println("It's not a FCH address.Input again. Enter to ignore");
            }
        }
    }

    public static <T> void writeToRedis(Object obj,String key, Jedis jedis, Class<T> tClass) {
        Map<String, String> settingMap = new HashMap<>();
        for (Field field : tClass.getDeclaredFields()) {
            field.setAccessible(true); // to access private fields
            try {
                Object value = field.get(obj);
                if (value != null) {
                    settingMap.put(field.getName(), String.valueOf(value));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to access field: " + field.getName(), e);
            }
        }
        jedis.hmset(key, settingMap);
    }

    public static <T> T readObjectFromRedisHash(Jedis jedis, String key, Class<T> clazz) {
        Map<String, String> properties = jedis.hgetAll(key);

        if (properties.isEmpty()) {
            System.out.println("No hash found with key: " + key);
            return null;
        }
        try {
        T instance = clazz.getDeclaredConstructor().newInstance();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            String value = properties.get(field.getName());
            if (value != null) {
                Class<?> type = field.getType();
                if (type == int.class || type == Integer.class) {
                    field.set(instance, Integer.parseInt(value));
                } else if (type == double.class || type == Double.class) {
                    field.set(instance, Double.parseDouble(value));
                } else if (type == boolean.class || type == Boolean.class) {
                    field.set(instance, Boolean.parseBoolean(value));
                } else {
                    field.set(instance, value); // Assuming String for other types
                }
            }
        }
        return instance;
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
