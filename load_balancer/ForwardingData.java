package load_balancer;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import load_balancer.*;


/**
 *  Data shared amongst request handler threads in order to forward requests
 */

public class ForwardingData{


    // Forwarding data
    private ConcurrentHashMap<Integer, ArrayList<String>> targetsByPart = new ConcurrentHashMap<Integer,ArrayList<String>>();
    private ConnectionPool connectionPool;
    private URLHash requestHash = null;
    private boolean useCaching;
    private CacheWithExpiry cache;  


    /* List of currently active targets, and queue of standby targets */
    private ArrayList<String> targets = new ArrayList<String>();
    private LinkedBlockingQueue<String> unresponsive = new LinkedBlockingQueue<String>();
    private LinkedList<String> standby = new LinkedList<String>();

    private int numPartitions = 0;
    private int replicationFactor;


    public ForwardingData(int poolSize, int replicationFactor, boolean useCaching) {
        this.connectionPool = new ConnectionPool(poolSize);
        this.requestHash = new URLHash(1);
        this.replicationFactor = replicationFactor;
        this.cache = new CacheWithExpiry();
        this.useCaching = useCaching;
    }

    // Supported operations on requestHash
    public synchronized void updateRequestHash(int numPartitions) {
        requestHash.setNumPartitions(numPartitions);
    }

    // Supported operations on targetsByPart
    public String getTarget(String shortResource, boolean is_write) {
        int partitionId = this.requestHash.hashDJB2(shortResource);

        int randIndex;
        String targetName;
        String targetInfo[];

        if (is_write) {
            targetName = targetsByPart.get(partitionId).get(0);
        } else {
            randIndex = ThreadLocalRandom.current().nextInt(0, targetsByPart.get(partitionId).size());
            targetName = targetsByPart.get(partitionId).get(randIndex);
        }

       return targetName;
    }

    public int assignPartitions() {
        targetsByPart.clear();

        int numTargets = targets.size();
        ArrayList<String> assigned; 

        for (int i = 0; i < numPartitions; i++) {
            assigned = new ArrayList<String>();
            for (int j = 0; j < replicationFactor; j++) {
                assigned.add(targets.get((i + j) % numTargets));
            }
            targetsByPart.put(i, assigned);
        }

        try (FileWriter fw = new FileWriter("config/manifest", false);
                BufferedWriter writer = new BufferedWriter(fw)) {
                for (Map.Entry<Integer, ArrayList<String>> entry: targetsByPart.entrySet()) {
                    int partId = entry.getKey();
                    ArrayList<String> assignedTargets = entry.getValue();
                    
                    String s = Integer.toString(partId);

                    for (int j = 0; j < assignedTargets.size(); j++){
                        s = s + "," + assignedTargets.get(j);
                    } 
                    writer.write(s);
                    writer.newLine();
                }
            } catch (IOException e) {
                return -1;
            }
        return 0;
    }

    public int rehashPairs() {
        // Open up a connection with each of the servers, and send the DISTRIBUTE request
        String targetInfo[];
        String hostname;
        int portnum;

        char[] reply = new char[4096];

        String msg;
        for (String targetName: targets) {
            targetInfo = targetName.split(":", 2);
            hostname = targetInfo[0];
            portnum = Integer.parseInt(targetInfo[1]);

            int bytesResponse = 0;
            
            try (Socket server = new Socket(hostname, portnum);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(server.getInputStream()));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()))){
                
                server.setSoTimeout(5000); // 5 seconds

                msg = String.format("DISTRIBUTE %d%n", numPartitions);
                writer.write(msg, 0, msg.length());
                writer.flush();
                
                try {
                    bytesResponse = reader.read(reply, 0, 4096);
                } catch (SocketTimeoutException e) {
                    System.err.format("Failed to receive acknowledgement for DISTRIBUTE from %s%n", hostname);

                } 
            } catch (IOException e) {
                return -1;
            }
        }
        return 0;
    }


    // Target management methods
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

    public LinkedBlockingQueue<String> getUnresponsiveTargets(){
        return unresponsive;
    }

    public LinkedList<String> getStandbyTargets() {
        return standby;
    }


    /**
     * Returns -1 if the target is unreachable, returns 0 
     * if a connection pool could be successfully established for this target
     */
    public int addTarget(String hostname, int portnum, boolean should_standby) {

        // Test the connection to the target 

        try {
            connectionPool.createConnectionPool(hostname + ":" + portnum);
        } catch (IOException e) {
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
        
        return 0;

    }

    /**
     * Returns -1 if the target could not be found, -2 if the target could not be replaced
     */
    public int removeTarget(String targetName, boolean should_replace) {
        // a replacement target is available

        int removeIndex = targets.indexOf(targetName);
        String replacement;
        if (removeIndex == -1) {
            return -1;
        }

        if (should_replace) {
            if (standby.isEmpty()) {
                return -1;
            } 
            replacement = standby.pollFirst();
            targets.set(removeIndex, replacement);
        } else {
            targets.remove(targetName);
            numPartitions -= 1;
            updateRequestHash(numPartitions);
        }

        // close the connections to the dead target
        try {
            connectionPool.destroyConnectionPool(targetName);
        } catch (IOException e) {}

        
        return 0;
    }
    
    public synchronized ArrayList<String> getTargets() {
        return this.targets;
    }

    // Connection establishment methods 
    public Socket connect(String targetName)
        throws IOException {
        return  connectionPool.connect(targetName);
    }

    public String getFromCache(String request) {
        if (cache.get(request) == null) {
            return null;
        }
        return cache.get(request);
    }

    public void cacheRequest(String request, String response) {
        cache.put(request, response);
    }

    public boolean isUsingCaching(){
        return useCaching;
    }


    public void closeConnection(String targetName, Socket s) 
        throws IOException {
            connectionPool.close(targetName, s);
    }

}