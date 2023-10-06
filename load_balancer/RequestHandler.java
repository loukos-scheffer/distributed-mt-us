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
    private final PrintWriter log;
    private ThreadData td;

    public RequestHandler(Socket client, ThreadData td, PrintWriter log) {
        this.client = client;
        this.td = td;
        this.log = log;
    }

    public void run() {

        String request = null;
        char[] reply = new char[4096];

        String hostname = null;
        int portnum = -1;
        int bytesRead = 0;

        
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

            // Process client request to determine target
            String shortResource = null;
            
            int partId;
            ArrayList<String> targets;
            
            String targetName;
            String targetInfo[];

        
            request = in.readLine();

            if (request == null) {
                System.err.println("Could not read input from client");
                return;
            }

            Pattern pput = Pattern.compile("^PUT\\s+/\\?short=(\\S+)&long=(\\S+)\\s+(\\S+)$");
            Matcher mput = pput.matcher(request);

            if (mput.matches()) {
                shortResource = mput.group(1);
            } else {
                Pattern pget = Pattern.compile("^(\\S+)\\s+/(\\S+)\\s+(\\S+)$");
                Matcher mget = pget.matcher(request);
                if (mget.matches()) {
                    shortResource = mget.group(1);
                } 
            }

            if (shortResource == null) {
                System.out.println("Could not extract short from client request");
                return;
            }
            
            targetName = td.getTarget(shortResource, mput.matches());
            targetInfo = targetName.split(":", 2);


            hostname = targetInfo[0];
            portnum = Integer.parseInt(targetInfo[1]);

            if (hostname == null || portnum < 0) {
                System.err.println("Could not obtain hostname or portnum for target");
                return;
            }

            // Forward the request to the server, and wait for the response
            
            Socket server;
            // take a socket from the pool of open connections
            try {
                server = td.getConnectionPool().openConnection(targetName);
            } catch (InterruptedException e){
                return;
            }

            try {
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()));
                BufferedReader reader = new BufferedReader(new InputStreamReader(server.getInputStream()));
                log.format("Forwarded %s to %s %n", request, hostname);
                request = String.format("%s%n", request);
                writer.write(request, 0, request.length());
                writer.flush();
                int bytesResponse = reader.read(reply, 0, 4096);
                if (bytesResponse == -1) {
                    System.err.println("Could not read response from server");
                }
            } catch (IOException e) {
                
                System.out.println(e);
                td.recordUnresponsiveTarget(hostname + ":" + portnum);
                
            }

            // Put the connection back in the pool
            try {
                td.getConnectionPool().closeConnection(server, targetName);
            } catch (InterruptedException e) {
                return;
            }
            
            // Lastly, write the server response to client 
            out.print(reply);
            out.flush();
                
        } catch (IOException e) {
            System.out.println("Could not read request from client");
        } finally {
            try {
                if (client != null) {
                    client.close();
                }
            } catch (IOException e) {}
        }
                
    }
        
} 
    

