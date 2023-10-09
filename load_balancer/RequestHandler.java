package load_balancer;

import java.io.*;
import java.nio.charset.*;
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
    private ForwardingData fd;
    private MonitoringData md;

    private static Pattern pput = Pattern.compile("^PUT\\s+/\\?short=(\\S+)&long=(\\S+)\\s+(\\S+)$");
    private static Pattern pget = Pattern.compile("^(\\S+)\\s+/(\\S+)\\s+(\\S+)$");


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
            String requestStr = null;

            try (InputStream in = new ByteArrayInputStream(request);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                requestStr = reader.readLine();
            } catch (IOException e) {}
            
            String hostname = null;
            int portnum = -1;

            String targetName;
            String targetInfo[];

            int bytesResponse = 0;
            int fileLength = 0;

            Socket server = null;

            Matcher mput = pput.matcher(requestStr);
            String shortResource = null;

            if (mput.matches()) {
                shortResource = mput.group(1);
            } else {
                Matcher mget = pget.matcher(requestStr);
                if (mget.matches()) {
                    shortResource = mget.group(2);
                }
            }

            if (shortResource == null) {
                System.err.println("Could not extract short from client request");
                return;
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
            }
            // Forward the request to the server, and wait for the response
            BufferedOutputStream streamToServer; 
            BufferedReader reader;

            try (
                streamToServer = new BufferedOutputStream(server.getOutputStream());
                reader = new BufferedReader(new InputStreamReader(server.getInputStream()))) {;
                streamToServer.write(request, 0, bytesRead);
                streamToServer.flush();
                bytesResponse = reader.read(response, 0, 4096);

                if (bytesResponse < 200) { 
                    bytesResponse += reader.read(response, bytesResponse, 4096 - bytesResponse);
                }
            } catch (IOException e) {
                System.err.println(e);
            }
            
            PrintWriter out;

            try {
                out = new PrintWriter(client.getOutputStream());
                out.print(response);
                out.flush();
            } catch (IOException e) {
                System.out.println("Could not return response to client");
            }
                
                
            try {
                fd.closeConnection(targetName, server);
                // server.close();
            } catch (IOException e) {
                System.err.println(e);
            }

            
            log.format("node=%s, short=%s %n", hostname, shortResource);
            md.recordSuccessfulRequest(targetName);
    
    }
}

    

