package load_balancer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class ConnectionPool{

    private ConcurrentHashMap<String, LinkedBlockingQueue<Socket>> socketsByTarget;
    private int poolSize;

    public ConnectionPool(int poolSize) {
        this.socketsByTarget = new ConcurrentHashMap<String, LinkedBlockingQueue<Socket>>();
        this.poolSize = poolSize;
    }
    
    public int createConnectionPool(String targetName) 
    {
        
        LinkedBlockingQueue<Socket> connections = new LinkedBlockingQueue<Socket>();
        socketsByTarget.put(targetName, connections);

        String targetInfo[] = targetName.split(":", 2);
        String hostname = targetInfo[0];
        int portnum = Integer.parseInt(targetInfo[1]);

        for (int i = 0; i < poolSize; i ++) {
            try {
                Socket socket = new Socket(hostname, portnum);
                connections.put(socket);
            } catch (IOException e) {
                return -1;
            } catch (InterruptedException e) {}
        }
        return 0;

    }

    public int destroyConnectionPool(String targetName) 
        {

        LinkedBlockingQueue<Socket> connections = socketsByTarget.get(targetName);
        for (Socket s: connections) {
            try{
                if (s != null) {
                    s.close();
                }
            } catch (IOException e) {
                return -1;
            }
        }
        socketsByTarget.remove(targetName);
        return 0;
    }


    public Socket openConnection(String targetName)
        throws InterruptedException {

            return socketsByTarget.get(targetName).take();

    }

    public void closeConnection(Socket server, String targetName) 
            throws InterruptedException {
            socketsByTarget.get(targetName).put(server);
    }


}