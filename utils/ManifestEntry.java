package utils;// this is a class to help us interpret the manifest

public class ManifestEntry {
    private String Node1 = null;
    private String Node2 = null;
    private int partitionNum;
    
    public ManifestEntry(int partitionNumber, String Node1, String Node2){
        this.partitionNum = partitionNumber;
        this.Node1 = Node1;
        this.Node2 = Node2;
    }

    public int getPartitionNum(){
        return this.partitionNum;
    }

    public String getNode1(){
        return this.Node1;
    }

    public String getNode2(){
        return this.Node2;
    }

    public int getPortNode1(){
        String[] map = this.Node1.split(":");
        return Integer.valueOf(map[1]);
    }

    public int getPortNode2(){
        String[] map = this.Node2.split(":");
        return Integer.valueOf(map[1]);
    }
    public String getHostnameNode1(){
        String[] map = this.Node1.split(":");
        return map[0];        
    }
    public String getHostnameNode2(){
        String[] map = this.Node2.split(":");
        return map[0];  
    }
}