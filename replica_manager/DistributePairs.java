package replica_manager;

import javaSQLite.DB;
import load_balancer.URLHash;
import utils.*;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class DistributePairs implements Runnable {
    private final String dbURL;
    DB db;
    URLHash urlHash;
    HashMap<Integer, ManifestEntry> manifestEntries;
    public boolean success = true;


    public DistributePairs(DB db, String dbURL, HashMap<Integer, ManifestEntry> manifestEntries){
        this.db = db;
        this.dbURL = dbURL;
        this.manifestEntries = manifestEntries;
        this.urlHash = new URLHash(this.manifestEntries.size());
    }
    
    public void run(){
        ArrayList<Row> dbDump = db.read();
        
        // iterate through dump
        for(int i = 0; i < dbDump.size(); i++){
            Row row = dbDump.get(i);

            int partitionNum = this.urlHash.hashDJB2(row.getShortURL());

            // determine which nodes to send to by checking manifest which is updated by URLShortener            
            ManifestEntry manifestEntry = this.manifestEntries.get(partitionNum);
            String hostnameNode1 = manifestEntry.getHostnameNode1();
            String hostnameNode2 = manifestEntry.getHostnameNode2();
            int portNode1 = manifestEntry.getPortNode1();
            int portNode2 = manifestEntry.getPortNode2();

            try {
                Socket socketNode1 = new Socket(hostnameNode1, portNode1);
                Socket socketNode2 = new Socket(hostnameNode2, portNode2);

                // create HTTP request to send to nodes
                String requestString = String.format("COPY %s %s\n", row.getShortURL(), row.getLongURL());

                // send to node 1, the replica manager on that end should handle the replication to node 2

                BufferedWriter node1Writer = new BufferedWriter(new OutputStreamWriter(socketNode1.getOutputStream()));
                BufferedReader node1Reader = new BufferedReader(new InputStreamReader(socketNode1.getInputStream()));

                node1Writer.write(requestString, 0, requestString.length());
                String line = null;

                line = node1Reader.readLine();

                if (!line.equals("200")) {
                    this.success = false;
                    System.out.println("COPY request to node 1 failed.");
                    // TODO replace with a log file
                }


                // write to second node now that we have ensured a correct delivery to node 1

                BufferedWriter node2Writer = new BufferedWriter(new OutputStreamWriter(socketNode2.getOutputStream()));
                BufferedReader node2Reader = new BufferedReader(new InputStreamReader(socketNode2.getInputStream()));

                node2Writer.write(requestString, 0, requestString.length());
                line = node2Reader.readLine();

                if (!line.equals("200")){
                    this.success = false;
                    System.out.println("COPY request to node 2 failed.");
                    // TODO replace with a log file
                }
            } catch (IOException e) {
                this.success = false;
                e.printStackTrace();
            }
        }
    }
    
}