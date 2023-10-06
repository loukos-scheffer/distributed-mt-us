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

    public URLHash(int numPartitions) {
        this.numPartitions = numPartitions;
    }

    public void setNumPartitions(int numPartitions) {
        this.numPartitions = numPartitions;
    }

    public int hashDJB2(String key){
        long hash = 5381;

        for (int i = 0; i < key.length(); i++) {
            hash = ((hash << 5) + hash) + key.charAt(i);
        }

        return (int) (hash % numPartitions + numPartitions) % numPartitions ;
    }

}