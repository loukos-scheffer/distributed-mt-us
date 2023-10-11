package load_balancer;

import java.net.Socket;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;


import load_balancer.*;

/**
 * Prints up-to-date monitoring info 
 */
public class MonitoringApp implements Runnable {

    private ForwardingData fd;
    private MonitoringData md;

    private final SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss a");
    private int reportingInterval = 5; // reporting interval in seconds

    

    public MonitoringApp(ForwardingData fd, MonitoringData md) {
        this.fd = fd;
        this.md = md;
    }
    public void run() {
        while (true) {
            try {
                Thread.sleep(reportingInterval * 1000);
                printRequestStatistics();
                if (Thread.interrupted()) {
                    return;
                }
            } catch (InterruptedException e) {
            }
        }
    }
    public void printRequestStatistics() {
        ConcurrentHashMap<String, Integer> successfulByTarget = md.getSuccessfulRequests();
        ConcurrentHashMap<String, Integer> failedByTarget = md.getFailedRequests();
        int totalRequests = 0;
        

        long successful;
        long failed;

        System.out.format("=== System State at %s ===%n", sdf.format(new Date()));
        System.out.format("Number of Requests by Target");


        for (String targetName: fd.getTargets()) {
            successful = (successfulByTarget.get(targetName) == null) ? 0 : successfulByTarget.get(targetName);
            failed = (failedByTarget.get(targetName) == null) ? 0 : failedByTarget.get(targetName);
            System.out.format("[%s] successful=%d failed=%d %n", targetName, successful, failed);
            totalRequests += successful;
        }

        
        System.out.println("Health Checking");
        for (String targetName: fd.getTargets()) {
            String status = fd.getUnresponsiveTargets().contains(targetName) ? "FAILED": "PASSED";
            System.out.format("[%s] Health Check %s%n", targetName, status);
        }

        long numCacheHits = md.getNumCacheHits();

        System.out.print("Caching");
        System.out.format("Cache hits=%d%n", numCacheHits);


        System.out.format("Request Statistics %n");
        System.out.format("Total requests=%d, Requests/second=%f %n%n", totalRequests, (float) totalRequests /reportingInterval);
        md.clearRequestStatistics();
    }
    private void printTargetList(Collection<String> targetList, String name) {
        System.out.print(name + ": ");
        for (String targetName: targetList) {
            System.out.print(targetName + " ");
        }
        System.out.println();
    } 

}

