package load_balancer;

import java.math.*;
import java.io.*;
import java.util.HashMap;

/**
 *  The URLHash class implements the algorithm for hashing short URLs
 *  to a partition ID.
 * 
 *  The hash function of choice is DJB2: https://theartincode.stanis.me/008-djb2/
 *  @Author: Keshav Worathur
 */

public class URLHash{

    private int numPartitions;


    public static void main(String args[]){

        int numPartitions = Integer.valueOf(args[0]);
        URLHash h = new URLHash(numPartitions);

        
        HashMap<Integer, Integer> countsById = new HashMap<Integer, Integer>();
        int partId;
        String shortURL;
        BufferedReader f;

        for (int i = 0; i < numPartitions; i++) {
            countsById.put(i, 0);
        }


        try {
            f = new BufferedReader(new FileReader("database.txt"));

            String line = f.readLine();
        
            while (line != null) {
                shortURL = line.split("\\s+")[0];
                partId = h.hashDJB2(shortURL);
                countsById.put(partId, countsById.get(partId) + 1);
                line = f.readLine();
            }

            countsById.forEach((key, value) -> System.out.println(key + " " + value));
        } catch (IOException e) {
            System.out.println("Exception occurred: " + e.getMessage());
        }

    }

    public URLHash(int numPartitions) {
        this.numPartitions = numPartitions;
    }

    int hashDJB2(String key){
        long hash = 5381;

        for (int i = 0; i < key.length(); i++) {
            hash = ((hash << 5) + hash) + key.charAt(i);
        }

        return (int) (hash % numPartitions + numPartitions) % numPartitions ;
    }

}