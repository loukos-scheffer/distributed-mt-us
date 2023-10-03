package load_balancer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class RequestHandler implements Runnable {

    private final Socket client;
    private final PrintWriter log;

    public RequestHandler(Socket client, PrintWriter log) {
        this.client = client;
        this.log = log;
    }

    public void run() {

        try {

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
                
                int randIndex;
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
                    System.err.print("Could not extract short from client request");
                }

                partId = ProxyServer.requestHash.hashDJB2(shortResource);
                targets = ProxyServer.targetsByPart.get(partId);
                
                randIndex = ThreadLocalRandom.current().nextInt(0, targets.size());
                
                targetInfo = targets.get(randIndex).split(":", 2);

                hostname = targetInfo[0];
                portnum = Integer.parseInt(targetInfo[1]);

                if (hostname == null || portnum < 0) {
                    System.err.println("Could not obtain hostname or portnum for target");
                    return;
                }

                // Forward the request to the server, and wait for the response
                try (Socket server = new Socket(hostname, portnum);
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()));
                     BufferedReader reader = new BufferedReader(new InputStreamReader(server.getInputStream()));
                    ) {
                    
                    log.format("Forwarded %s to %s %n", request, hostname);
                    request = String.format("%s%n", request);
                    writer.write(request, 0, request.length());
                    writer.flush();
                    int bytesResponse = reader.read(reply, 0, 4096);

                    if (bytesResponse == -1) {
                        System.err.println("Could not read response from server");
                    }
                } catch (IOException e) {

                    try {
                        ProxyServer.unresponsive.put(hostname + ":" + portnum);
                    } catch (InterruptedException i) {}
                }

                // Lastly, write the server response to client 
                out.print(reply);
                out.flush();
            }
        catch (IOException e) {
            System.err.println(e);
        }
        } finally {
            try {
                if (client != null) {
                    client.close();
                }
            } catch (IOException e) {}
        }
    }
}
