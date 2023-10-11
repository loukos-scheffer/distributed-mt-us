package load_balancer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

import load_balancer.*;


public class HealthChecker implements Runnable {

    private int healthCheckInterval = 5; // in seconds

    private ForwardingData fd;

    public HealthChecker(ForwardingData fd) {
        this.healthCheckInterval = healthCheckInterval;
        this.fd = fd;
    }

    public void run() {

        String targetInfo[];
        String hostname;
        int portnum;

        String msg;

        char[] reply = new char[4096];
        int bytesRead = 0;
        boolean isUnresponsive;

        while (true) {

            if (Thread.interrupted()) {
                return;
            }
            try{ 
                Thread.sleep(healthCheckInterval * 1000);

                for (String targetName: fd.getTargets()) {
                    isUnresponsive=false;
                    targetInfo = targetName.split(":", 2);
                    hostname = targetInfo[0];
                    portnum = Integer.parseInt(targetInfo[1]);

                    try (Socket server = new Socket(hostname, portnum);
                         BufferedReader reader = new BufferedReader(new InputStreamReader(server.getInputStream()));
                         BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()));
                    ){
                        // send a request for a long URL hello
                        msg = String.format("GET /hello HTTP/1.1%n");
                        writer.write(msg, 0, msg.length());
                        writer.flush();

                        // multiple lines should be returned as an HTTP response. 
                        bytesRead = reader.read(reply, 0, 4096);
                        
                        if (bytesRead == -1) {
                            isUnresponsive = true;
                        }

                    } catch (IOException e){ // could not connect to host
                        isUnresponsive = true;                        
                    }

                    if (isUnresponsive & !fd.getUnresponsiveTargets().contains(targetName)) {
                        fd.recordUnresponsiveTarget(targetName);
                    }

                }
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}