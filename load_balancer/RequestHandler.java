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
    }

    public void run() {


        final byte[] request = new byte[1024];
        int bytesRead;

        try {
            BufferedInputStream streamFromClient = new BufferedInputStream(client.getInputStream());
            bytesRead = streamFromClient.read(request);
            handleRequest(request, bytesRead);            
        } catch (IOException e) {
            System.err.println("Client conection dropped before request could be forwarded");
        } 
    }


    public void handleRequest(byte[] request, int bytesRead) {

            
            String requestHead = null;

            try (InputStream in = new ByteArrayInputStream(request);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                requestHead = reader.readLine();
            } catch (IOException e) {
                return;
            }
            
            
            String hostname = null;
            int portnum = -1;

            String targetName;
            String targetInfo[];

            Socket server;

            Matcher mput = pput.matcher(requestHead);
            String shortResource = null;

            boolean isCacheable = false;

            if (mput.matches()) {
                shortResource = mput.group(1);
                isCacheable = false;
            } else {
                Matcher mget = pget.matcher(requestHead);
                if (mget.matches()) {
                    shortResource = mget.group(2);
                    isCacheable = true;
                }
            }
            isCacheable = isCacheable && fd.isUsingCaching();

            if (shortResource == null) {
                System.err.format("Could not extract short from request: %s", requestHead);
                return;
            }
            
            if (isCacheable) { // cache GET requests only

                String cachedResponse = fd.getFromCache(shortResource);

                if (cachedResponse != null) {
                    try {
                        PrintWriter streamToClient = new PrintWriter(client.getOutputStream());
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
                // server = new Socket(hostname, portnum); 
                
       
            } catch (IOException e){
                System.err.format("Unable to establish connection with %s %n", hostname);
                md.recordFailedRequest(targetName);
                return;
            }
            // Forward the request to the server, and wait for the response
            
           
             // One thread to forward the request to the server
            Thread forward = new Thread(new WriteToServer(server, request, bytesRead));
            forward.start();

            Thread backward = new Thread(new ReadFromServer(server, client, isCacheable, shortResource, requestHead, targetName, log, fd, md));
            backward.start();

            return;
    }
        
} 
    
class WriteToServer implements Runnable {


    Socket server;
    byte[] request;
    int bytesRead;

    public WriteToServer(Socket server, byte[] request, int bytesRead) {
        this.server = server;
        this.request = request;
        this.bytesRead = bytesRead;
    }

    public void run() {

        try {
            BufferedOutputStream streamToServer = new BufferedOutputStream(server.getOutputStream());
            

            System.out.println(new String(request, "UTF-8"));
            
            streamToServer.write(request, 0, bytesRead);
            
            streamToServer.flush();
        } catch (IOException e) {}

    }
}

class ReadFromServer implements Runnable {

    private Socket server;
    private Socket client;
    private boolean isCacheable;
    private String shortResource;
    private String requestHead;
    private String targetName;
    private PrintWriter log;
    private ForwardingData fd;
    private MonitoringData md;

    public ReadFromServer(Socket server, Socket client, boolean isCacheable, String shortResource, String requestHead, 
                         String targetName, PrintWriter log, ForwardingData fd, MonitoringData md) {
        this.server = server;
        this.client = client;
        this.isCacheable = isCacheable;
        this.shortResource = shortResource;
        this.requestHead = requestHead;
        this.targetName = targetName;
        this.log = log;
        this.fd = fd;
        this.md = md;
    }

    public void run() {
        int bytesResponse;
        int fileLength;
        char[] response = new char[4096];


        try {
            BufferedReader streamFromServer = new BufferedReader(new InputStreamReader(server.getInputStream()));
            

            bytesResponse = streamFromServer.read(response, 0, 2048);
            

            PrintWriter streamToClient = new PrintWriter(client.getOutputStream());
            streamToClient.write(response);
            streamToClient.flush();
        } catch (SocketTimeoutException e) {

        } catch (IOException e) {
            System.out.println(e);
            System.out.println("Could not return response to client");
            md.recordFailedRequest(targetName);
            return;
        }

        if (isCacheable) {
            fd.cacheRequest(shortResource, String.valueOf(response));
        }

        try {
            fd.closeConnection(targetName, server);
            // server.close();
        } catch (IOException e) {
            System.err.println(e);
        }
        log.format("Forwarded %s to %s %n", requestHead, targetName);
        md.recordSuccessfulRequest(targetName);
    }
}


