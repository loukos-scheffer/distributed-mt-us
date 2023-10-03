package load_balancer;

import java.util.regex.*;

/**
 * Defines the communication protocol that orchestration scripts will use to interact
 * with the load balancer
 * @Author Keshav Worathur
 */

public class AdminProtocol {

    public static final Pattern INIT = Pattern.compile("^INIT");
    public static final Pattern ADD = Pattern.compile("^ADD\\s+(\\S+)\\s+(\\S+)");
    public static final Pattern RM = Pattern.compile("^REMOVE\\s+(\\S+)\\s+(\\S+)");
    public static final Pattern STANDBY = Pattern.compile("^STANDBY\\s+(\\S+)\\s+(\\S+)");
    public static final Pattern STOP = Pattern.compile("^STOP");
    
}