package load_balancer;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


import load_balancer.*;

public class CacheWithExpiry{

    private Map<String, ArrayList<Object>> map = new ConcurrentHashMap<String, ArrayList<Object>>();
    private long expiryInMillis = 3000;

    public CacheWithExpiry() {
        initialize();
    }

    public void initialize() {
        Thread cleaner = new Thread(new CacheCleaner(map, expiryInMillis));
        cleaner.start();    
    }

    public void put(String key, String value) {
        long currentTime = new Date().getTime();
        map.put(key, new ArrayList<Object>(Arrays.asList(value, currentTime)));
        return;
    }

    public String get(String key) {
        if (map.get(key) == null) {
            return null;
        } 
        return String.valueOf(map.get(key).get(0));
    }



}

class CacheCleaner implements Runnable {

    private final Map<String, ArrayList<Object>> map;
    private final long expiryInMillis;

    public CacheCleaner (Map<String, ArrayList<Object>> map, long expiryInMillis) {
        this.map = map;
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
        for (String key: map.keySet()) {
            if (currentTime > ((long) map.get(key).get(1) + expiryInMillis)) {
                map.remove(key);
            }
        }
    }


}