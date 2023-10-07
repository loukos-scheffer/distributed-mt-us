package load_balancer;

import java.net.Socket;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import load_balancer.*;

/**
 * Prints up-to-date monitoring info 
 */
public class MonitoringApp implements Runnable {

    private ForwardingData fd;
    private MonitoringData md;

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
        long maxRTT = md.getMaxRTT();

        long successful;
        long failed;


        System.out.println("=====================");
        for (String targetName: successfulByTarget.keySet()) {
            successful = (successfulByTarget.get(targetName) == null) ? 0 : successfulByTarget.get(targetName);
            failed = (failedByTarget.get(targetName) == null) ? 0 : failedByTarget.get(targetName);
            System.out.format("Node=%s, successful=%d, failed=%d %n", targetName, successful, failed);
            totalRequests += successful;
        }
        printTargetList(fd.getUnresponsiveTargets(), "Unresponsive");
        printTargetList(fd.getTargets(), "Active");
        printTargetList(fd.getStandbyTargets(), "Standby");
        System.out.format("Total requests=%d, Requests/second=%f, Max RTT=%d %n", totalRequests, (float) totalRequests /reportingInterval ,maxRTT);
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

