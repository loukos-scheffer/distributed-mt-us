package replica_manager;

import javaSQLite.DB;
import utils.ManifestEntry;
import utils.ManifestReader;

import load_balancer.URLHash;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;

public class CopyPair implements Runnable {
    String shortURL;
    String longURL;
    String currentHostname;
    HashMap<Integer, ManifestEntry> manifestEntries;
    private URLHash hash;

    public CopyPair(String shortURL, String longURL, String currentHostname, HashMap<Integer, ManifestEntry> manifestEntries){
        this.shortURL = shortURL;
        this.longURL = longURL;
        this.currentHostname = currentHostname;
        this.manifestEntries = manifestEntries;
        int totalPartitions = manifestEntries.keySet().size();
        this.hash = new URLHash(totalPartitions);
    }

    public void run(){
        int partitionId = hash.hashDJB2(shortURL);

        ManifestEntry manifestEntry = this.manifestEntries.get(partitionId);
        String hostnameNode1 = manifestEntry.getHostnameNode1();
        String hostnameNode2 = manifestEntry.getHostnameNode2();
        int portNode1 = manifestEntry.getPortNode1();
        int portNode2 = manifestEntry.getPortNode2();
        String requestString = String.format("COPY %s %s\n", this.shortURL, this.longURL);
        System.out.println("COPYPAIR " + requestString);
        try {
            if (this.currentHostname.equals(hostnameNode1)) {
                Socket socketNode2 = new Socket(hostnameNode2, portNode2);

                BufferedWriter node2Writer = new BufferedWriter(new OutputStreamWriter(socketNode2.getOutputStream()));
                BufferedReader node2Reader = new BufferedReader(new InputStreamReader(socketNode2.getInputStream()));

                node2Writer.write(requestString, 0, requestString.length());
                node2Writer.flush();
                String line = node2Reader.readLine();
                String[] lineMap = line.split(" ");
                System.out.println("IN COPYPAIR" + line);

                if (!lineMap[1].startsWith("200")){
                    System.out.println("COPY request to node 2 failed.");
                    Thread.sleep(100);
                    // retry
                    socketNode2 = new Socket(hostnameNode2, portNode2);

                    node2Writer = new BufferedWriter(new OutputStreamWriter(socketNode2.getOutputStream()));
                    node2Reader = new BufferedReader(new InputStreamReader(socketNode2.getInputStream()));

                    node2Writer.write(requestString, 0, requestString.length());
                    line = node2Reader.readLine();
                    lineMap = line.split(" ");
                    if (!lineMap[1].startsWith("200")){
                        System.out.println("COPY request to node 2 failed twice.");
                    }
                    socketNode2.close();

                }
                try {
                    socketNode2.close();
                } catch (IOException e) {
                    System.err.println("Failed to close socket");
                }
            }
            else if (this.currentHostname.equals(hostnameNode2)){
                Socket socketNode1 = new Socket(hostnameNode1, portNode1);

                BufferedWriter node1Writer = new BufferedWriter(new OutputStreamWriter(socketNode1.getOutputStream()));
                BufferedReader node1Reader = new BufferedReader(new InputStreamReader(socketNode1.getInputStream()));

                node1Writer.write(requestString, 0, requestString.length());
                String line = node1Reader.readLine();
                String[] lineMap = line.split(" ");

                if (!lineMap[1].startsWith("200")){
                    System.out.println("COPY request to node 1 failed.");
                    Thread.sleep(100);
                    // retry on failure

                    socketNode1 = new Socket(hostnameNode1, portNode1);

                    node1Writer = new BufferedWriter(new OutputStreamWriter(socketNode1.getOutputStream()));
                    node1Reader = new BufferedReader(new InputStreamReader(socketNode1.getInputStream()));

                    node1Writer.write(requestString, 0, requestString.length());
                    line = node1Reader.readLine();
                    lineMap = line.split(" ");
                    if (!lineMap[1].startsWith("200")){
                        System.out.println("COPY request to node 1 failed twice.");
                    }
                    socketNode1.close();
                }

                try {
                    socketNode1.close();
                } catch (IOException e) {}

            } else {
                System.out.println("did not match either hostname in the manifest.");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}