package replica_manager;

import javaSQLite.DB;
import load_balancer.URLHash;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReplicaManager {
    static final String MANIFEST = "./config/manifest";
    private final ExecutorService workers;
    private final String currentHostname;
    private final String dbURL;
    //    URLHash hashRequest = null;
    DB db = null;



    public ReplicaManager (int numWorkers, DB db, String dbURL, String currentHostname) {
        this.workers = Executors.newFixedThreadPool(numWorkers);
        this.currentHostname = currentHostname;
//        this.hashRequest= new URLHash(partitions);
        this.db = db;
        this.dbURL = dbURL;
    }

    public boolean replicate(byte[] request){
        //request is of the format PUT shortURL longURL\n
        String requestString = new String(request, StandardCharsets.UTF_8);
        String[] lineMap = requestString.split(" ");
        String shortURL = lineMap[1];
        String longURL = lineMap[2];

        CopyPair copyPair = new CopyPair(shortURL, longURL, this.currentHostname);

        workers.execute(copyPair);
        return copyPair.success;
    }

    public boolean distribute(){
        DistributePairs distributePairs = new DistributePairs(this.db, this.dbURL);

        workers.execute(distributePairs);
        return distributePairs.success;
    }
}
