package load_balancer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import load_balancer.ProxyServer;

public class RequestHandler implements Runnable {

    private final Socket client;
    // Stream to which a summary of the request will be written
    private final PrintWriter log;
    private final PrintWriter err;
    private ForwardingData fd;
    private MonitoringData md;

    private static Pattern pput = Pattern.compile("^PUT\\s+/\\?short=(\\S+)&long=(\\S+)\\s+(\\S+)$");
    private static Pattern pget = Pattern.compile("^(\\S+)\\s+/(\\S+)\\s+(\\S+)$");


    public RequestHandler(Socket client, ForwardingData fd, MonitoringData md, PrintWriter log, PrintWriter err) {
        this.client = client;
        this.fd = fd;
        this.md = md;
        this.log = log;
        this.err = err;
    }

    public void run() {
        
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {
            String request = in.readLine();
            handleRequest(request, out);

        } catch (IOException e) {
            System.err.println("Could not read request from client");
        } finally {
            try {
                if (client != null) {
                    client.close();
                }
            } catch (IOException e) {}
        }       
    }


    public void handleRequest(String request, PrintWriter out) {

            char[] reply = new char[4096];

            String hostname = null;
            int portnum = -1;
            

            String targetName;
            String targetInfo[];

            int bytesRead = 0;
            int bytesResponse = 0;

            Socket server = null;

            Matcher mput = pput.matcher(request);
            String shortResource = null;

            if (mput.matches()) {
                shortResource = mput.group(1);
            } else {
                Matcher mget = pget.matcher(request);
                if (mget.matches()) {
                    shortResource = mget.group(1);
                }
            }

            if (shortResource == null) {
                return;
            }
            
            targetName = fd.getTarget(shortResource, mput.matches());
            targetInfo = targetName.split(":", 2);

            hostname = targetInfo[0];
            portnum = Integer.parseInt(targetInfo[1]);

            if (hostname == null || portnum < 0) {
                err.println("Could not obtain hostname or portnum for target");
                return;
            }


            try {
                server = fd.connect(targetName);
            } catch (IOException e){
                err.format("Unable to establish connection with %s %n", hostname);
            }
            // Forward the request to the server, and wait for the response
            try {
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()));
                BufferedReader reader = new BufferedReader(new InputStreamReader(server.getInputStream()));
                
                
                request = String.format("%s%n", request);
                writer.write(request, 0, request.length());
                writer.flush();

                bytesResponse = reader.read(reply, 0, 4096);
                if (bytesResponse == -1) {
                    System.err.println("Could not read response from server");
                }
            } catch (IOException e) {
                err.format("Request forwarding failed: target %s unreachable %n", hostname);
                // fd.recordUnresponsiveTarget(hostname + ":" + portnum);
            }

            try {
                fd.closeConnection(targetName, server);
            } catch (IOException e) {
                md.recordFailedRequest(targetName);
                return;
            }



            // log.format("node=%s, short=%s %n", hostname, shortResource);
            md.recordSuccessfulRequest(targetName);
            


            // Lastly, write the server response to client 
            out.print(reply);
            out.flush();
    
    }
        
} 
    

