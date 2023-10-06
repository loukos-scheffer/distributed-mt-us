package load_balancer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import load_balancer.*;

/**
 * Main class for the reverse proxy load balancer.
 */
public class ProxyServer {

    // Shared thread data
    private final ThreadData td;

    // Performance parameters
    private final int poolSize=8;
    private final int numHandlers=8;


    public ProxyServer() {
        this.td = new ThreadData(poolSize);
    }

    public int initService() {

        // Read the targets in from config/hosts, testing if each target is reachable 
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
                error = td.addTarget(hostname, portnum, false);

                if (error != 0) {
                    System.err.format("[ERROR] Target %d unreachable %n", hostname);
                }
                line = r.readLine();
            }  
        } catch (IOException e) {
            System.err.println("[ERROR] config/hosts file does not exist");
        }

        if (td.getTargets().size() == 0) {
            System.err.println("[ERROR] Unable to initialize at least one target");
            return -1; // could not connect to any targets
        }

        return 0;
    }


    public void stopService(Thread lbWorker) {
        try {
            String hostname;
            int portnum;
            int error;

            lbWorker.interrupt();
            for (String targetName: td.getTargets()) {
                error = td.removeTarget(targetName, false);
            }

        } catch (SecurityException e) {
            System.err.println(e);
        }
    }

    
    public void startProxyServer(){

        try {

            // Create one socket to listen for service requests and another 
            // to listen to scaling actions

            
        
            // Create a server socket
            int localPort = 8080;
            int adminPort = 8081;

            boolean serviceStarted = false;

            // Start a thread that listens for unresponsive target events
            Thread targetRecycler = new Thread() {
                public void run() {
                    String targetName;
                    for (;;) {
                        targetName = td.pollUnresponsiveTargets();
                        if (targetName != null) {
                            td.removeTarget(targetName, true);
                        }
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
                                out.println("[ERROR] No targets added");
                            } else {
                                out.format("Load balancer started on port %d %n", localPort);
                            }

                            lb = new Thread(new LoadBalancer(localPort, numHandlers, td));
                            lb.start();
                            serviceStarted = true;
                            
                        } else if ((m = ap.ADD.matcher(cmd)).matches()) {
                            hostname = m.group(1);
                            portnum = Integer.parseInt(m.group(2));
                            error = td.addTarget(hostname, portnum, false);

                            if (error != 0) {
                                out.format("Unable to add target %s:%d %n", hostname, portnum);
                            } else {
                                out.println("Added target");
                            }
                            
                        } else if ((m = ap.RM.matcher(cmd)).matches()) {
                            hostname = m.group(1);
                            portnum = Integer.parseInt(m.group(2));
                            error = td.removeTarget(hostname + ":" + portnum, false);

                            if (error == 1) {
                                out.println("Could not find target");
                            } else if (error == 2) {
                                out.format("Could not replace target %s with a standby target. %n", hostname);
                                System.exit(1);
                            }
                            
                        } else if ((m = ap.STANDBY.matcher(cmd)).matches()) {
                            hostname = m.group(1);
                            portnum = Integer.parseInt(m.group(2));
                            error = td.addTarget(hostname, portnum, true);
                            if (error != 0) {
                                out.format("Could not connect to target %s %n", hostname);
                            } else {
                                out.format("Added target %s on standby %n", hostname);
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
    private ThreadData td;
    private final ExecutorService pool;
    private final PrintWriter log = new PrintWriter(new FileWriter("log/traffic_log.txt"), true);

    public LoadBalancer(int localPort, int numWorkers, ThreadData td) 
        throws IOException {
        this.serverSocket = new ServerSocket(localPort);
        this.pool = Executors.newFixedThreadPool(numWorkers);
        this.td = td;
    }

    public void run() {
        try {
            for (;;) {
                pool.execute(new RequestHandler(serverSocket.accept(), td, log));
            }
        } catch (IOException e) {
            pool.shutdown();
            System.err.println(e);
        }
    }
}


