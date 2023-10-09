package load_balancer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;


/**
 * A pool of connections. A set of <poolSize> connections is created for 
 * each active target.
 * 
 */
public class ConnectionPool{

    private ConcurrentHashMap<String, LinkedBlockingQueue<Socket>> socketsByTarget;
    private int poolSize;

    public ConnectionPool(int poolSize) {
        this.socketsByTarget = new ConcurrentHashMap<String, LinkedBlockingQueue<Socket>>();
        this.poolSize = poolSize;
    }
    
    public void createConnectionPool(String targetName) 
        throws IOException {
        
        LinkedBlockingQueue<Socket> connections = new LinkedBlockingQueue<Socket>();
        socketsByTarget.put(targetName, connections);

        String targetInfo[] = targetName.split(":", 2);
        String hostname = targetInfo[0];
        int portnum = Integer.parseInt(targetInfo[1]);

        for (int i = 0; i < poolSize; i ++) {
            try {
                Socket socket = new Socket(hostname, portnum);
<<<<<<< HEAD
=======
                socket.setSoTimeout(1000);
>>>>>>> load_balancer
                connections.put(socket);
            }  catch (InterruptedException e) {
                throw new IOException("Could not place connection in pool");
            }
        }
    }

    public void destroyConnectionPool(String targetName) 
        throws IOException {

        LinkedBlockingQueue<Socket> connections = socketsByTarget.get(targetName);
        for (Socket s: connections) {
            if (s != null) {
                s.close();
            }
        }
        socketsByTarget.remove(targetName);
    }


    public Socket connect(String targetName)
        throws IOException {
            try {
                return socketsByTarget.get(targetName).take();
            } catch (InterruptedException e) {
                throw new IOException("Could not take connection from pool");
            }

    }

    public void close(String targetName, Socket server) 
            throws IOException {
            try {
                socketsByTarget.get(targetName).put(server);
            } catch (InterruptedException e) {
                throw new IOException("Could not place connection back in pool");
            }
    }


}