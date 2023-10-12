package replica_manager;

import javaSQLite.DB;
import load_balancer.URLHash;
import utils.*;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class DistributePairs implements Runnable {
    DB db;
    URLHash urlHash;
    HashMap<Integer, ManifestEntry> manifestEntries;
    String hostname;
    public boolean success = true;


    public DistributePairs(DB db, HashMap<Integer, ManifestEntry> manifestEntries, String hostname){
        this.db = db;
        this.manifestEntries = manifestEntries;
        this.urlHash = new URLHash(this.manifestEntries.size());
        this.hostname = hostname;
    }
    
    public void run(){
        ArrayList<Row> dbDump = db.read();
        boolean hostmatch = false;
        ArrayList<Row> nonMatchingRows = new ArrayList<Row>();
        
        // iterate through dump
        for (Row row : dbDump) {
            int partitionNum = this.urlHash.hashDJB2(row.getShortURL());

            // determine which nodes to send to by checking manifest which is updated by URLShortener            
            ManifestEntry manifestEntry = this.manifestEntries.get(partitionNum);
            String hostnameNode1 = manifestEntry.getHostnameNode1();
            String hostnameNode2 = manifestEntry.getHostnameNode2();
            int portNode1 = manifestEntry.getPortNode1();
            int portNode2 = manifestEntry.getPortNode2();
            // create HTTP request to send to nodes
            String requestString = String.format("COPY %s %s\n", row.getShortURL(), row.getLongURL());
            String line = null;
            String[] lineMap = null;

            try {
                if (!this.hostname.equals(hostnameNode2)) {
                    Socket socketNode1 = new Socket(hostnameNode1, portNode1);


                    // send to node 1, the replica manager on that end should handle the replication to node 2

                    BufferedWriter node1Writer = new BufferedWriter(new OutputStreamWriter(socketNode1.getOutputStream()));
                    BufferedReader node1Reader = new BufferedReader(new InputStreamReader(socketNode1.getInputStream()));

                    node1Writer.write(requestString, 0, requestString.length());
                    line = node1Reader.readLine();

                    lineMap = line.split(" ");

                    if (!lineMap[1].startsWith("200")) {

                        System.out.println("COPY request to node 1 failed.");
                        Thread.sleep(100);

                        socketNode1 = new Socket(hostnameNode1, portNode1);

                        node1Writer = new BufferedWriter(new OutputStreamWriter(socketNode1.getOutputStream()));
                        node1Reader = new BufferedReader(new InputStreamReader(socketNode1.getInputStream()));

                        node1Writer.write(requestString, 0, requestString.length());
                        line = node1Reader.readLine();
                        lineMap = line.split(" ");
                        if (!lineMap[1].startsWith("200")) {
                            this.success = false;
                            System.out.println("COPY request to node 1 failed twice.");
                        }
                        socketNode1.close();
                    }
                } else{
                    hostmatch = true;
                }

                if (!this.hostname.equals(hostnameNode2)) {

                    // write to second node now that we have ensured a correct delivery to node 1
                    Socket socketNode2 = new Socket(hostnameNode2, portNode2);

                    BufferedWriter node2Writer = new BufferedWriter(new OutputStreamWriter(socketNode2.getOutputStream()));
                    BufferedReader node2Reader = new BufferedReader(new InputStreamReader(socketNode2.getInputStream()));

                    node2Writer.write(requestString, 0, requestString.length());
                    line = node2Reader.readLine();
                    lineMap = line.split(" ");

                    if (!lineMap[1].startsWith("200")) {
                        this.success = false;
                        System.out.println("COPY request to node 2 failed.");

                        Thread.sleep(100);
                        // retry
                        socketNode2 = new Socket(hostnameNode2, portNode2);

                        node2Writer = new BufferedWriter(new OutputStreamWriter(socketNode2.getOutputStream()));
                        node2Reader = new BufferedReader(new InputStreamReader(socketNode2.getInputStream()));

                        node2Writer.write(requestString, 0, requestString.length());
                        line = node2Reader.readLine();
                        lineMap = line.split(" ");
                        if (!lineMap[1].startsWith("200")) {
                            this.success = false;
                            System.out.println("COPY request to node 2 failed twice.");
                        }
                        socketNode2.close();
                    }
                } else{
                    hostmatch = true;
                }

                if (!hostmatch){
                    nonMatchingRows.add(row);
                }
            } catch (IOException | InterruptedException e) {
                this.success = false;
                e.printStackTrace();
            }
        }
        // remove pairs from DB if not matching either host.
        System.out.println(nonMatchingRows);
        
        db.batch_delete(nonMatchingRows);
    }
    
}