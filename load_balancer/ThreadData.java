package load_balancer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import load_balancer.*;


/**
 * Stores all shared data for the reverse proxy load balancer.
 * 
 */
public class ThreadData{


    private ConcurrentHashMap<Integer, ArrayList<String>> targetsByPart = new ConcurrentHashMap<Integer,ArrayList<String>>();
    private ConnectionPool connectionPool;
    private URLHash requestHash = null;

    private LinkedBlockingQueue<String> unresponsive = new LinkedBlockingQueue<String>();
    private int numPartitions = 0;

    /* List of currently active targets, and queue of standby targets */
    private ArrayList<String> targets = new ArrayList<String>();
    private LinkedList<String> standby = new LinkedList<String>();


    // Monitoring info
    private ConcurrentHashMap<String, Integer> requestsByTarget = new ConcurrentHashMap<Integer, ArrayList<String>>();

    public ThreadData(int poolSize) {
        this.connectionPool = new ConnectionPool(poolSize);
        this.requestHash = new URLHash(4);
    }

    public void updateRequestHash(int numPartitions) {
        requestHash.setNumPartitions(numPartitions);
    }

    public String getTarget(String shortResource, boolean is_write) {
        int partitionId = this.requestHash.hashDJB2(shortResource);

        int randIndex;
        String targetName;
        String targetInfo[];

        if (is_write) {
            targetName = targetsByPart.get(partitionId).get(0);
        } else {
            randIndex = ThreadLocalRandom.current().nextInt(0, targets.size());
            targetName = targetsByPart.get(partitionId).get(randIndex);
        }

       return targetName;
    }

    public void recordUnresponsiveTarget(String targetName) {
        try{
            this.unresponsive.put(targetName);
        } catch (InterruptedException e) {
            return;
        }
    }


    public String pollUnresponsiveTargets() {
        try {
            return this.unresponsive.take();
        } catch (InterruptedException e) {
            return null;
        }
    }

    public void assignPartitions() {
        int numTargets = targets.size();

        for (int i = 0; i < numPartitions; i++) {
            int h1 = i % numTargets;
            int h2 = (i + 1) % numTargets;
            targetsByPart.put(i, new ArrayList<>(Arrays.asList(targets.get(h1), targets.get(h2))));
        }

        try (FileWriter fw = new FileWriter("config/manifest.txt");
                BufferedWriter writer = new BufferedWriter(fw)) {
                for (Map.Entry<Integer, ArrayList<String>> entry: targetsByPart.entrySet()) {
                    int partId = entry.getKey();
                    ArrayList<String> hosts = entry.getValue();

                    String s = Integer.toString(partId);

                    for (int j = 0; j < hosts.size(); j++){
                        s = s + "," + hosts.get(j);
                    } 
                    writer.write(s);
                    writer.newLine();
                }
            } catch (IOException e) {}
    }

    /**
     * Returns -1 if the target is unreachable, returns 0 
     * if a connection pool could be successfully established for this target
     */
    public int addTarget(String hostname, int portnum, boolean should_standby) {

        // Test the connection to the target 
        int error;
        error = connectionPool.createConnectionPool(hostname + ":" + portnum);
        if (error != 0) {
            System.out.format("Target %s:%d unreachable %n", hostname, portnum);
            return -1;
        }

        if (should_standby) {
            standby.add(hostname + ":" + portnum);
        } else {
            targets.add(hostname + ":" + portnum);
        }

        // recompute partitions
        numPartitions += 1;
        updateRequestHash(numPartitions);
        assignPartitions();
        return 0;

    }

    /**
     * Returns -1 if the target could not be found, -2 if the target could not be replaced
     */
    public int removeTarget(String targetName, boolean should_replace) {


        // a replacement target is available
        if (should_replace && !standby.isEmpty()) {
            int i = targets.indexOf(targetName);
            if (i == -1) {
                return -1;
            }
            String newTarget = standby.pollFirst();
            targets.set(i, newTarget);
        } else { // scale in 

            targets.remove(targetName);
            numPartitions -= 1;
            updateRequestHash(numPartitions);
            
            if (numPartitions <= 0) {
                System.exit(0);
            }

        
            if (should_replace) {
                // System.err.format("Unable to replace failed target %s, no available standby targets %n", targetName);
                return -2;
            }
        }

        // close the connections to the dead target
        connectionPool.destroyConnectionPool(targetName);
        assignPartitions();
        return 0;
    }

    
    public synchronized ArrayList<String> getTargets() {
        return this.targets;
    }

    public synchronized ConnectionPool getConnectionPool() {
        return this.connectionPool;
    }

}