package load_balancer;

import java.io.*;
import java.nio.charset.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import load_balancer.ProxyServer;

public class RequestHandler implements Runnable {

    private final Socket client;
    // Stream to which a summary of the request will be written
    private final PrintWriter log;
    private ForwardingData fd;
    private MonitoringData md;

    private final Pattern pput = Pattern.compile("^PUT\\s+/\\?short=(\\S+)&long=(\\S+)\\s+(\\S+)$");
    private final Pattern pget = Pattern.compile("^(\\S+)\\s+/(\\S+)\\s+(\\S+)$");


    public RequestHandler(Socket client, ForwardingData fd, MonitoringData md, PrintWriter log) {
        this.client = client;
        this.fd = fd;
        this.md = md;
        this.log = log;
        this.err = err;
    }

    public void run() {


        final byte[] request = new byte[1024];
        int bytesRead;

        try (BufferedInputStream streamFromClient = new BufferedInputStream(client.getInputStream());) {
            bytesRead = streamFromClient.read(request);
            handleRequest(request, bytesRead);
        } catch (IOException e) {
            System.err.println("Client conection dropped before request could be forwarded");
        } finally {
            try {
                if (client != null) {
                    client.close();
                }
            } catch (IOException e) {}
        }       
    }


    public void handleRequest(byte[] request, int bytesRead) {

            char[] response = new char[4096];
            String requestHead = null;

            try (InputStream in = new ByteArrayInputStream(request);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                requestHead = reader.readLine();
            } catch (IOException e) {}
            
            
            String hostname = null;
            int portnum = -1;

            String targetName;
            String targetInfo[];

            int bytesResponse = 0;

            Socket server = null;

            PrintWriter streamToClient;
            BufferedOutputStream streamToServer; 
            BufferedReader streamFromServer;

            Matcher mput = pput.matcher(requestHead);
            String shortResource = null;

            boolean isCacheable = false;

            if (mput.matches()) {
                shortResource = mput.group(1);
            } else {
                Matcher mget = pget.matcher(requestHead);
                if (mget.matches()) {
                    shortResource = mget.group(2);
                    isCacheable = true;
                }
            }

            isCacheable = fd.isUsingCaching();

            if (shortResource == null) {
                System.err.format("Could not extract short from request: %s", requestHead);
                return;
            }
            
            if (isCacheable) { // cache GET requests only

                String cachedResponse = fd.getFromCache(shortResource);

                if (cachedResponse != null) {
                    try {
                        streamToClient = new PrintWriter(client.getOutputStream());
                        streamToClient.print(cachedResponse);
                        streamToClient.flush();
                        md.recordCacheHit();
                        log.format("Serviced %s from cache %n", requestHead);
                    } catch (IOException e) {
                        System.err.format("Could not return response to client from cache");
                    }
                    return;
                }

            }
            
            targetName = fd.getTarget(shortResource, mput.matches());
            targetInfo = targetName.split(":", 2);

            hostname = targetInfo[0];
            portnum = Integer.parseInt(targetInfo[1]);

            if (hostname == null || portnum < 0) {
                System.err.println("Could not obtain hostname or portnum for target");
                return;
            }
	    
	    

            try {
                server = fd.connect(targetName);
                // server = new Socket(hostname, portnum); If pooling was not used
            } catch (IOException e){
                System.err.format("Unable to establish connection with %s %n", hostname);
                md.recordFailedRequest(targetName);
                return;
            }
            // Forward the request to the server, and wait for the response
            

            try {
                streamToServer = new BufferedOutputStream(server.getOutputStream());
                streamFromServer = new BufferedReader(new InputStreamReader(server.getInputStream()));
                streamToServer.write(request, 0, bytesRead);
                streamToServer.flush();

                bytesResponse = streamFromServer.read(response, 0, 4096);
                bytesResponse += streamFromServer.read(response, bytesResponse, 4096 - bytesResponse);
                
            } catch (SocketTimeoutException e) {
                System.err.format("Incomplete response for %s from %s%n", requestHead, hostname);
                md.recordFailedRequest(targetName);
                return;
            } catch (IOException e) {
                System.err.println(e);
            }
            
            

            try {
                streamToClient = new PrintWriter(client.getOutputStream());
                streamToClient.print(response);
                streamToClient.flush();
            } catch (IOException e) {
                System.out.println("Could not return response to client");
                md.recordFailedRequest(targetName);
                return;
            }

            if (isCacheable) {
                fd.cacheRequest(shortResource, String.valueOf(response));
            }
            
            try {
                fd.closeConnection(targetName, server);
                // server.close(); if pooling was not used
            } catch (IOException e) {
                System.err.println(e);
            }
            
            log.format("Forwarded %s to %s %n", requestHead, hostname);
            md.recordSuccessfulRequest(targetName);
    
    }
        
} 
    

