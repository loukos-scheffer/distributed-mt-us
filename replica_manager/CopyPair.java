package replica_manager;

import javaSQLite.DB;
import utils.ManifestEntry;
import utils.ManifestReader;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;

public class CopyPair implements Runnable {
    String shortURL;
    String longURL;
    String currentHostname;
    int partitionNum;
    HashMap<Integer, ManifestEntry> manifestEntries;
    public boolean success = true;

    public CopyPair(String shortURL, String longURL, String currentHostname){
        this.shortURL = shortURL;
        this.longURL = longURL;
        this.currentHostname = currentHostname;
        this.manifestEntries = new ManifestReader().mapManifestEntries();
    }

    public void run(){
        ManifestEntry manifestEntry = this.manifestEntries.get(this.partitionNum);
        String hostnameNode1 = manifestEntry.getHostnameNode1();
        String hostnameNode2 = manifestEntry.getHostnameNode2();
        int portNode1 = manifestEntry.getPortNode1();
        int portNode2 = manifestEntry.getPortNode2();
        String requestString = String.format("COPY %s %s\n", this.shortURL, this.longURL);
        try {
            if (this.currentHostname.equals(hostnameNode1)) {
                Socket socketNode2 = new Socket(hostnameNode2, portNode2);

                BufferedWriter node2Writer = new BufferedWriter(new OutputStreamWriter(socketNode2.getOutputStream()));
                BufferedReader node2Reader = new BufferedReader(new InputStreamReader(socketNode2.getInputStream()));

                node2Writer.write(requestString, 0, requestString.length());
                String line = node2Reader.readLine();

                if (!line.equals("200")){
                    this.success = false;
                    System.out.println("COPY request to node 2 failed.");
                    // TODO replace with a log file
                }
            }
            else if (this.currentHostname.equals(hostnameNode2)){
                Socket socketNode1 = new Socket(hostnameNode1, portNode1);

                BufferedWriter node1Writer = new BufferedWriter(new OutputStreamWriter(socketNode1.getOutputStream()));
                BufferedReader node1Reader = new BufferedReader(new InputStreamReader(socketNode1.getInputStream()));

                node1Writer.write(requestString, 0, requestString.length());
                String line = node1Reader.readLine();

                if (!line.equals("200")){
                    this.success = false;
                    System.out.println("COPY request to node 1 failed.");
                    // TODO replace with a log file
                }

            } else {
                System.out.println("did not match either hostname in the manifest.");
            }
        } catch (IOException e) {
            e.getMessage();
        }
    }
}