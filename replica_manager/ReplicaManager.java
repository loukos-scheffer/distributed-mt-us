package replica_manager;

import javaSQLite.DB;
import utils.ManifestEntry;
import utils.ManifestReader;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReplicaManager {
    private final ExecutorService workers;
    private final String currentHostname;
    private final String dbURL;
    private static HashMap<Integer, ManifestEntry> manifestEntries;
    DB db = null;




    public ReplicaManager (String dbURL, String currentHostname) {
        this.workers = Executors.newFixedThreadPool(4);
        this.manifestEntries = new ManifestReader().mapManifestEntries();
        this.currentHostname = currentHostname;
        this.db = new DB();
        this.dbURL = dbURL;
    }

    public boolean replicate(byte[] request){
        //request is of the format PUT shortURL longURL\n
        String requestString = new String(request, StandardCharsets.UTF_8);
        System.out.println(requestString);
        Pattern pput = Pattern.compile(
            "^PUT\\s+/\\?short=(\\S+)&long=(\\S+)\\s+(\\S+)$"
        );
        Matcher mput = pput.matcher(requestString);

        if (mput.matches()) {
            String shortURL = mput.group(1);
            String longURL = mput.group(2);

            CopyPair copyPair = new CopyPair(shortURL, longURL, this.currentHostname, this.manifestEntries);
            workers.execute(copyPair);
        }

        return true;
    }

    public boolean distribute(){
        this.manifestEntries = new ManifestReader().mapManifestEntries();
        DistributePairs distributePairs = new DistributePairs(this.db, this.dbURL, this.manifestEntries);

        workers.execute(distributePairs);
        return distributePairs.success;
    }
}
