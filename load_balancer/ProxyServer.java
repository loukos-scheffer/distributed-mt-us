package load_balancer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;


public class ProxyServer {

    /* Data shared with threads that handle requests */
    public static ConcurrentHashMap<Integer, ArrayList<String>> targetsByPart = new ConcurrentHashMap<Integer,ArrayList<String>>();
    public static URLHash requestHash;
    public static LinkedBlockingQueue<String> unresponsive = new LinkedBlockingQueue<String>();

    /* Replication information */
    private final int replicationFactor = 2;
    private int numPartitions = 0;
    private int numTargets = 0;

    /* List of currently active targets, and queue of standby targets */
    private ArrayList<String> targets = new ArrayList<String>();
    private LinkedList<String> standby = new LinkedList<String>();

    private int initService() {

        // Read the targets in from config/hosts, testing if each target is reachable 
        try(FileReader reader = new FileReader("config/hosts.txt");
            BufferedReader r = new BufferedReader(reader)) {
            String line;
            String hostname;
            int portNum;

            line = r.readLine();

            while (line != null) {
                String hostInfo[] = line.split("\\s+");
                hostname = hostInfo[0];
                portNum = Integer.parseInt(hostInfo[1]);
                try (Socket server = new Socket(hostname, portNum)){
                    targets.add(hostname + ":" + portNum);
                } catch (IOException e) {
                    System.err.format("Target %s:%d unreachable %n", hostname, portNum);
                }
                line = r.readLine();
            }  
        } catch (IOException e) {}

        if (targets.isEmpty()) {
            return -1; // unable to proceed with load balancing
        }

        numTargets =  targets.size();
        numPartitions = numTargets;
        requestHash = new URLHash(numPartitions);
        assignPartitions();

        return 0;

    }


    private int addTarget(String hostname, int portnum, boolean should_standby) {
        
        // Test the connection to the target 
        try (Socket client = new Socket(hostname, portnum)) {
            if (should_standby) {
                standby.add(hostname + ":" + portnum);
            } else {
                targets.add(hostname + ":" + portnum);
            }
        } catch (IOException e) {
            System.err.format("Target %s:%d unreachable %n", hostname, portnum);
            return -1;
        }

        // recompute partitions
        numPartitions += 1;
        requestHash = new URLHash(numPartitions);
        assignPartitions();
        return 0;
    }

    private int removeTarget(String targetName, boolean replace) {

        if (replace && !standby.isEmpty()) {
            int i = targets.indexOf(targetName);
            if (i == -1) {
                return 1;
            }
            String newTarget = standby.pollFirst();
            targets.set(i, newTarget);
        } else {
            targets.remove(targetName);
            numPartitions -= 1;
            requestHash = new URLHash(numPartitions);

            if (replace) {
                System.err.format("Unable to replace failed target %s, no available standby targets %n", targetName);
                return 2;
            }
        }

        assignPartitions();
        return 0;
    }

    private void stopService(Thread lb) {
        try {
            lb.interrupt();
        } catch (SecurityException e) {
            System.err.println(e);
        }

    }

    private void assignPartitions() {
        // assign targets to partitions and store this information in targetsByPart HashMap
        // copy this information a manifest file to be used by the targets
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

    public void startProxyServer(){

        try {

            // Create a server socket
            int loadBalancerPort = 8080;
            int adminPort = 8081;

            boolean serviceStarted = false;

            // Start a thread that listens for unresponsive target events
            Thread targetRecycler = new Thread() {
                public void run() {
                    String targetName;
                    for (;;) {
                        try {
                            targetName = ProxyServer.unresponsive.take();
                            removeTarget(targetName, true);
                        } catch (InterruptedException e) {}
                    }
                }
            };


            targetRecycler.start();

            // In the main thread run a server which listens to scaling events
            ServerSocket adminServer = new ServerSocket(adminPort);
            Thread lb = null;

            // Define the admin server protocol
            AdminProtocol ap = new AdminProtocol();
            
            while (true) {

                try (Socket client = adminServer.accept();
                    InputStreamReader reader  = new InputStreamReader(client.getInputStream());
                    BufferedReader in = new BufferedReader(reader);
                    PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

                    String cmd;
                    String hostname;
                    int portnum;
                    int error;

                    while ((cmd = in.readLine()) != null) {
                        Matcher m;
                        if (!serviceStarted && ap.INIT.matcher(cmd).matches()) {
                            error = initService();
                            if (error != 0) {
                                out.println("No targets added");
                            } else {
                                out.println("READY");
                            }

                            lb = new Thread(new LoadBalancer(loadBalancerPort));
                            lb.start();
                            serviceStarted = true;
                            
                        } else if ((m = ap.ADD.matcher(cmd)).matches()) {
                            hostname = m.group(1);
                            portnum = Integer.parseInt(m.group(2));
                            error = addTarget(hostname, portnum, false);

                            if (error != 0) {
                                out.format("Unable to add target %s:%d %n", hostname, portnum);
                            } else {
                                out.println("Added target");
                            }
                            
                        } else if ((m = ap.RM.matcher(cmd)).matches()) {
                            hostname = m.group(1);
                            portnum = Integer.parseInt(m.group(2));
                            error = removeTarget(hostname + ":" + portnum, false);

                            if (error == 1) {
                                out.println("Could not find target");
                            } else if (error == 2) {
                                out.format("Could not replace target %s with a standby target. %n", hostname);
                                System.exit(1);
                            }
                            
                        } else if ((m = ap.STANDBY.matcher(cmd)).matches()) {
                            hostname = m.group(1);
                            portnum = Integer.parseInt(m.group(2));
                            error = addTarget(hostname, portnum, true);
                            if (error != 0) {
                                out.println("Could not connect to target %s %n", hostname);
                            } else {
                                out.println("Added target %s on standby %n", hostname);
                            }
                        } else if (ap.STOP.matcher(cmd).matches()) {
                            out.println("Stopping service");
                            stopService(lb);
                            System.exit(0);
                        } else {
                            out.println("Command not recognized");
                        }
                    }                
                } catch (IOException e) {
                    System.err.println("Error when accepting connection from admin");
                }
            }
        } catch (IOException e) {}
            
    }


    public static void main(String[] args) throws IOException {
            ProxyServer ps = new ProxyServer();
            ps.startProxyServer();
    }
}



class LoadBalancer implements Runnable {

    private final ServerSocket serverSocket;
    private final ExecutorService pool;
    private final PrintWriter log = new PrintWriter(new FileWriter("lb_events.txt"), true);

    public LoadBalancer(int localPort) 
        throws IOException {
        serverSocket = new ServerSocket(localPort);
        System.out.format("Load balancer started on port %d %n", localPort);
        pool = Executors.newFixedThreadPool(16);
    }

    public void run() {
        try {
            for (;;) {
                pool.execute(new RequestHandler(serverSocket.accept(), log));
            }
        } catch (IOException e) {
            pool.shutdown();
            System.err.println(e);
        }
    }
}


