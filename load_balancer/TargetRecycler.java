package load_balancer;


import load_balancer.*;

/**
 * Listens for unresponsive nodes and replaces them with nodes from the 
 * standby queue
 */

public class TargetRecycler implements Runnable {

    private final ForwardingData fd;

    public TargetRecycler(ForwardingData fd) {
        this.fd = fd;
    }

    public void run() {
        String targetName;
        int error;
        for (;;) {
            targetName = fd.pollUnresponsiveTargets();
            error = fd.removeTarget(targetName, true);
            if (error == -1) {
                System.out.format("[CRITICAL] Could not replace target %s with a standby target. %n", targetName);
                try {
                    fd.recordUnresponsiveTarget(targetName);
                    Thread.sleep(5000);
                } catch (InterruptedException e) {}
            }
        }
    }

}