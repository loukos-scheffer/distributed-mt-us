package utils;// this is a class to help us interpret the manifest

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class ManifestReader {
    static final String MANIFEST = "./config/manifest";

    public static HashMap<int, ManifestEntry> mapManifestEntries() {
        try {
            File file = new File(MANIFEST);
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line;
            HashMap<int, ManifestEntry> manifestEntries = new HashMap<int, ManifestEntry>();
            while ((line = bufferedReader.readLine()) != null) {          
                String[] map = line.split(",");
                int partitionNumber = Integer.getInteger(map[0]);
                String hostOne = map[1];
                String hostTwo = map[2];

                ManifestEntry manifestEntry = new ManifestEntry(partitionNumber, hostOne, hostTwo);

                manifestEntries.put(partitionNumber, manifestEntry);
            }
            fileReader.close();
            return manifestEntries;
        } catch (IOException e) {
        return null;
        }
    }
}