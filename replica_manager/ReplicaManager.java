package replica_manager;

import javaSQLite.DB;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReplicaManager {
    private final ExecutorService workers;
    private final String currentHostname;
    private final String dbURL;
    DB db = null;



    public ReplicaManager (String dbURL, String currentHostname) {
        this.workers = Executors.newFixedThreadPool(4);
        this.currentHostname = currentHostname;
        this.db = new DB();
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
