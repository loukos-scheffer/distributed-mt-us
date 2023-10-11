package load_balancer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import load_balancer.*;

/**
 *  Starts all tasks, and listens for scaling commands from an admin
 */
public class ProxyServer {

    // Shared thread data
    private final ForwardingData fd;
    private final MonitoringData md;

    // To control throughput
    private final int poolSize=64;
    private final int numHandlers=32;
    private final int replicationFactor=2;
    private final boolean useCaching = false;



    public ProxyServer() 
        throws IOException {
        this.fd = new ForwardingData(poolSize, replicationFactor, useCaching);
        this.md = new MonitoringData();
    }

    public int initService() {

        // Read the targets in from config/hosts, testing if each target is reachable 
        String threadName = Thread.currentThread().getName();

        try(FileReader reader = new FileReader("config/hosts");
            BufferedReader r = new BufferedReader(reader)) {
            String line;
            String hostname;
            int portnum;
            int error;

            line = r.readLine();

            while (line != null) {
                String hostInfo[] = line.split(":");
                hostname = hostInfo[0];
                portnum = Integer.parseInt(hostInfo[1]);
                error = fd.addTarget(hostname, portnum, false);

                if (error != 0) {
                    System.err.format("[%s] Target %s unreachable %n", threadName, hostname);
                }
                line = r.readLine();
            }  
        } catch (IOException e) {
            System.err.format("[%s] config/hosts file does not exist %n", threadName);
        }

        if (fd.getTargets().size() == 0) {
            System.err.format("[%s] Unable to initialize at least one target %n", threadName);
            return -1; // could not connect to any targets
        }

        return 0;
    }

    public void stopService() {
       
    }

    public void startProxyServer(){

        try {

            // Create one socket to listen for service requests and another 
            // to listen to scaling actions
            int localPort = 8080;
            int adminPort = 8081;

            boolean serviceStarted = false;

            Thread loadBalancer = null;
            try {
                loadBalancer = new Thread(new LoadBalancer(localPort, numHandlers, fd, md));
            } catch (IOException e) {
                System.err.format("Could not bind load balancer to port %d %n", localPort);
                System.exit(0);
            }

            // Start a thread that listens for unresponsive target events
            Thread targetRecycler = new Thread(new TargetRecycler(fd));
            Thread monitoringApp = new Thread(new MonitoringApp(fd, md));
            Thread healthChecker = new Thread(new HealthChecker(fd));
            
            // In the main thread run a server which listens to scaling events
            ServerSocket adminServer = new ServerSocket(adminPort);
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
                            if (error == -1) {
                                out.println("[ERROR] No targets added");
                                System.exit(1);
                            } 
                                
                            
                            error = fd.assignPartitions();

                            if (error == -1) {
                                out.println("Failed to write updated info to manifest file");
                                System.exit(1);
                            }

                            out.println("Waiting for reply for DISTRIBUTE from each target");
                            error = fd.rehashPairs();
                            if (error == -1) {
                                out.println("Rehashing failed or partially completed");
                                System.exit(1);
                            }

                            out.format("Load balancer started on port %d %n", localPort);
                            loadBalancer.start();
                            targetRecycler.start();
                            monitoringApp.start();
                            healthChecker.start();

                            serviceStarted=true;
                            
                        } else if ((m = ap.ADD.matcher(cmd)).matches()) {
                            hostname = m.group(1);
                            portnum = Integer.parseInt(m.group(2));
                            error = fd.addTarget(hostname, portnum, false);
                            
                            if (error != 0) {
                                out.format("Unable to add target %s:%d %n", hostname, portnum);
                            } else {
                                out.format("Added target %s:%d %n", hostname, portnum);
                                fd.assignPartitions();
                                fd.rehashPairs();
                            }
                            
                        } else if ((m = ap.RM.matcher(cmd)).matches()) {
                            hostname = m.group(1);
                            portnum = Integer.parseInt(m.group(2));
                            error = fd.removeTarget(hostname + ":" + portnum, false);
                            if (error == -1) {
                                out.format("Could not find target %s%n", hostname);
                            }  else{
                                out.format("Removed target %s:%d%n", hostname, portnum);
                                fd.assignPartitions();
                                fd.rehashPairs();
                            }

                        } else if ((m = ap.STANDBY.matcher(cmd)).matches()) {
                            hostname = m.group(1);
                            portnum = Integer.parseInt(m.group(2));
                            error = fd.addTarget(hostname, portnum, true);
                            if (error != 0) {
                                out.format("Could not connect to target %s %n", hostname);
                            } else {
                                out.format("Added target %s on standby %n", hostname);
                                fd.assignPartitions();
                                fd.rehashPairs();
                            }
                        } else if (ap.STOP.matcher(cmd).matches()) {
                            out.println("Stopping service");
                            targetRecycler.interrupt();
                            monitoringApp.interrupt();
                            healthChecker.interrupt();
                            loadBalancer.interrupt();
                            System.exit(0);
                        } else {
                            out.println("Command not recognized");
                        }
                    }                
                } catch (IOException e) {
                    System.err.println("Could not accept connection from admin: " + e.getMessage());
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

    private ForwardingData fd;
    private MonitoringData md;

    // private final ExecutorService pool;
    private final PrintWriter log;

    public LoadBalancer(int localPort, int numWorkers, ForwardingData fd, MonitoringData md) 
        throws IOException {
        this.serverSocket = new ServerSocket(localPort);
        // this.pool = Executors.newFixedThreadPool(numWorkers);
        this.fd = fd;
        this.md = md;
        this.log = new PrintWriter(new FileWriter("log/traffic_log.txt"), true);
    }

    public void run() {
        try {
            for (;;) {
                Thread t = new Thread(new RequestHandler(serverSocket.accept(), fd, md, log));
                t.start();
            }
        } catch (IOException e) {
            // pool.shutdown();
            System.err.println(e);
        } 
    }
}