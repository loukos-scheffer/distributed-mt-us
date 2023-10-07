package load_balancer;

import java.util.*;
import java.util.concurrent.*;

import load_balancer.*;

/**
 * Monitoring app data produced by request handlers and consumed by the monitoring app
 */
public class MonitoringData {

    // Two hashmaps successful requests by target, failed requests by target
    private ConcurrentHashMap<String, Integer> successfulByTarget = new ConcurrentHashMap<String, Integer>();
    private ConcurrentHashMap<String, Integer> failedByTarget = new ConcurrentHashMap<String, Integer>();
    
    
    private long maxRTT = 0;


    public void recordSuccessfulRequest(String targetName) {
        incrementValue(successfulByTarget, targetName);
    }

    public void recordFailedRequest(String targetName) {
        incrementValue(failedByTarget, targetName);
    }

    public void clearRequestStatistics() {
        successfulByTarget.clear();
        failedByTarget.clear();
        maxRTT = 0;    
    }


    public ConcurrentHashMap<String, Integer> getSuccessfulRequests() {
        return successfulByTarget;
    }

    public ConcurrentHashMap<String, Integer> getFailedRequests() {
        return failedByTarget;
    }

    private void incrementValue(ConcurrentHashMap<String, Integer> map, String key) {
        map.compute(key, (k, v) -> (v == null) ? 1: ((int) v + 1));
    }

    public synchronized void setMaxRTT(long recordedRTT) {
        maxRTT = Math.max(maxRTT, recordedRTT);
    }

    public long getMaxRTT(){
        return maxRTT;
    }

}