package load_balancer;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;


import load_balancer.*;

public class CacheWithExpiry{

    private Map<String, String> map = new ConcurrentHashMap<String, String>();
    private Map<String, Long> timeStamps = new ConcurrentHashMap<String, Long>();

    private long expiryInMillis = 3000;

    public CacheWithExpiry() {
        initialize();
    }

    public void initialize() {
        Thread cleaner = new Thread(new CacheCleaner(map, timeStamps, expiryInMillis));
        cleaner.start();    
    }

    public void put(String key, String value) {
        long currentTime = new Date().getTime();
        map.put(key, value);
        timeStamps.put(key, currentTime);
        return;
    }

    public String get(String key) {
        return map.get(key);
    }

}

class CacheCleaner implements Runnable {

    private final Map<String, String> map;
    private final Map<String, Long> timeStamps;
    private final long expiryInMillis;

    public CacheCleaner (Map<String, String> map, Map<String, Long> timeStamps, long expiryInMillis) {
        this.map = map;
        this.timeStamps = timeStamps;

        this.expiryInMillis = expiryInMillis;
    }

    public void run() {
        while (true) {
            cleanMap();
            try{
                Thread.sleep(expiryInMillis / 2);
            } catch (InterruptedException e) {
                System.out.println(e);
            }
        }
    }


    private void cleanMap() {
        long currentTime = new Date().getTime();
        for (String key: timeStamps.keySet()) {
            if (currentTime > ((long) timeStamps.get(key) + expiryInMillis)) {
                map.remove(key);
                timeStamps.remove(key);
            }
        }
    }


}