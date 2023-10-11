

import java.util.ConcurrentHashMap;


public class BatchWriter implements Runnable {

    private ConcurrentHashMap<String, String> pendingWrites;
    private int maxPending = 50;

    public BatchWriter() {
        this.pendingWrites = new ConcurrentHashMap<String, String>();

    }

    public void addWrite(String shortURL, String longURL) {
        pendingWrites.put(shortURL, longURL);
    }

    public void run() {


        Thread t = new Thread(){
            public void run() {

                while (true) {
                    try {
                        Thread.sleep(5000);
                        if (pendingWrites.size() >= maxPending) {
                            DB.write()
                        }
                    } catch (InterruptedException e) {}
                }
            }
        };

        t.start();
    }

    

}