import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class RequestHandler implements Runnable {

    private Socket client;

    public RequestHandler(Socket client) {
        this.client = client;
    }

    public void run() {

        try {

            String request = null;
            char[] reply = new char[4096];

            String hostname = null;
            int portNum = -1;
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

                partId = ServerStarter.requestHash.hashDJB2(shortResource);
                
                targets = ProxyServer.targetsByPart.get(partId);
                
                randIndex = ThreadLocalRandom.current().nextInt(0, targets.size());
                
                targetInfo = targets.get(randIndex).split(":", 2);
                // System.out.format("randIndex %d targets.get(randIndex) %s %n", randIndex, targets.get(randIndex));
                
                hostname = targetInfo[0];
                portNum = Integer.parseInt(targetInfo[1]);

                if (hostname == null || portNum < 0) {
                    System.err.println("Could not obtain hostname or portnum for target");
                    return;
                }

                // Forward the request to the server, and wait for the response
                try (Socket server = new Socket(hostname, portNum);
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()));
                     BufferedReader reader = new BufferedReader(new InputStreamReader(server.getInputStream()));
                    ) {
                    
                    System.out.format("Forwarding Request %s to %s %n", request, hostname);
                    request = String.format("%s%n", request);
                    writer.write(request, 0, request.length());
                    writer.flush();
                    int bytesResponse = reader.read(reply, 0, 4096);
                } catch (IOException e) {
                    System.err.println(e);
                }

                // Lastly, write server response to client 
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
