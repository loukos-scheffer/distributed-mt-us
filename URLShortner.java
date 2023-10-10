import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.http.*;
import java.time.Duration;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javaSQLite.*;
import replica_manager.ReplicaManager;

import java.util.*;
import java.util.concurrent.*;

public class URLShortner {

  static final File WEB_ROOT = new File(".");
  static final String FILE_NOT_FOUND_HTML = "<html>\n\t<body>\n\t\t<h1>Not Found</h1>\n\t</body>\n</html>\r\n";
  static final String INTERNAL_SERVER_ERROR_HTML = "<html>\n\t<body>\n\t\t<h1>INTERNAL SERVER ERROR</h1>\n\t</body>\n</html>\r\n";
  static final String REDIRECT_RECORDED_HTML = "<html>\n\t<body>\n\t\t<h1>Got it!</h1>\n\t</body>\n</html>\r\n";
  static final String REDIRECT_HTML = "<html>\n\t<head>\n\t\t<title>Moved</title>\n\t</head>\n\t<body>\n\t\t<h1>Moved</h1>\n\t\t<p>This page has moved</p>\n\t</body>\n</html>\r\n";
  static final String BAD_REQUEST_HTML = "<html>\n\t<body>\n\t\t<h1>400 - BAD REQUEST</h1>\n\t</body>\n</html>\r\n";
  static final String DATABASE = "database.txt";
  static final String MANIFEST = "./config/manifest";
  static String HOSTNAME = null;
  static String HOSTNAMEPORT = null;
  // port to listen connection
  static int PORT = 59958;
  static ReplicaManager replicaManager;

  public static String PARTITION_1_NAME = "part1";
  public static String PARTITION_1_BACKUP_HOST = "http://dh2026pc12:59958/";
  public static String PARTITION_2_NAME = "part2";
  public static String PARTITION_2_BACKUP_HOST = "http://dh2026pc12:59958/";

  static DB DB = null;
  static String DB_URL = "jdbc:sqlite:/virtual/daidkara/example.db";

  static volatile int consecutive_failures = 0;
  static final int consecutive_failure_limit = 10;

  // verbose mode
  static final boolean verbose = true;

  public static void main(String[] args) {
    try {
      if (args.length != 2) {
        System.out.println("CORRECT USAGE: PORT, DB_URL");
        return;
      }
      PORT = Integer.parseInt(args[0]);
      DB_URL = args[1];
      HOSTNAME = InetAddress.getLocalHost().getHostName();
      HOSTNAMEPORT = HOSTNAME + ":" + PORT;
      replicaManager = new ReplicaManager(DB_URL, HOSTNAME);
      DB = new DB();
      System.out.println("Attempting to start server on: " + HOSTNAMEPORT);
      ServerSocket serverConnect = new ServerSocket(PORT);
      System.out.println(
        "Server started.\nListening for connections on port : " +
        PORT +
        " ...\n"
      );
      System.out.println("Reading Parition Information from manifest...");
      // boolean readSuccess = updateFromManifest();
      // if (readSuccess) {
      //   System.out.println("Sucessfully read partition information");
      // } else {
      //   System.out.println("FAILED TO READ MANIFEST");
      // }
      // we listen until user halts server execution
      while (true) {
        if (verbose) {
          System.out.println("Connecton opened. (" + new Date() + ")");
        }
        HandleRequestWorker worker = new HandleRequestWorker(
          serverConnect.accept()
        );
        new Thread(worker).start();
      }
    } catch (IOException e) {
      System.err.println("Server Connection error : " + e.getMessage());
    }
  }

  
  private static class HandleRequestWorker implements Runnable {

    private Socket connectionSocket;

    public HandleRequestWorker(Socket connection) {
      this.connectionSocket = connection;
    }

    public boolean isRequestHeader(String request) {

      ArrayList<String> cmds = new ArrayList<String>(Arrays.asList("PUT", "GET", "DISTRIBUTE", "COPY"));

      for (String cmd: cmds) {
        if (request.startsWith(cmd)) {
          return true;
        }
      }
      return false;

    }


    public void run() {
      Socket connect = this.connectionSocket;
      BufferedReader in = null;
      PrintWriter out = null;
      BufferedOutputStream dataOut = null;




      try {
        in =
          new BufferedReader(new InputStreamReader(connect.getInputStream()));
        out = new PrintWriter(connect.getOutputStream());
        dataOut = new BufferedOutputStream(connect.getOutputStream());
        String line;
        String input;

      
        while ((line = in.readLine()) != null) {
          if (isRequestHeader(line)) {
            input = line;
            if (verbose) System.out.println("first line: " + input);

            Pattern distributeput = Pattern.compile(
              "^DISTRIBUTE\\s+(\\S+)$"
            );
            Matcher distributemput = distributeput.matcher(input);
            if (distributemput.matches()) {
              String numHosts = distributemput.group(1);
              System.out.println("DISTRIBUTE TO " + numHosts);
              
              //call replicaManager to distribute the urls,
              replicaManager.distribute();
              out.println("HTTP/1.1 200 OK");
              out.println("Server: Java HTTP Server/Shortner : 1.0");
              out.println("Date: " + new Date());
              out.println();
              out.flush();
            }

            Pattern setbackupput = Pattern.compile(
              "^COPY\\s+(\\S+)\\s+(\\S+)$"
            );
            Matcher setbackupmput = setbackupput.matcher(input);
            if (setbackupmput.matches()) {
              String shortResource = setbackupmput.group(1);
              String longResource = setbackupmput.group(2);
              System.out.println(
                "SAVING BACKUP: " + shortResource + " " + longResource
              );
              boolean saved = save(shortResource, longResource);
              if(saved) {
                out.println("HTTP/1.1 200 OK");
                out.println();
                out.flush();
              } else {
                out.println("HTTP/1.1 500 Internal Server Error");
                out.println();
                out.flush();
              }
            }

            Pattern pput = Pattern.compile(
              "^PUT\\s+/\\?short=(\\S+)&long=(\\S+)\\s+(\\S+)$"
            );
            Matcher mput = pput.matcher(input);
            if (mput.matches()) {
              String shortResource = mput.group(1);
              String longResource = mput.group(2);
              String httpVersion = mput.group(3);

              if (
                shortResource == null ||
                shortResource.isEmpty() ||
                longResource == null ||
                longResource.isEmpty()
              ) {
                  out.println("HTTP/1.1 400 BAD REQUEST");
                  out.println("Server: Java HTTP Server/Shortner : 1.0");
                  out.println("Date: " + new Date());
                  out.println("Content-type: text/html");
                  out.println("Content-length: 63");
                  out.println();
                  out.println(BAD_REQUEST_HTML);
                  out.flush();
              } else {
                if (!replicaManager.replicate(input.getBytes())){
                  out.println("HTTP/1.1 500 Internal Server Error");
                  out.println("Server: Java HTTP Server/Shortner : 1.0");
                  out.println("Date: " + new Date());
                  out.println("Content-type: text/html");
                  out.println("Content-length: 67");
                  out.println();
                  out.println(INTERNAL_SERVER_ERROR_HTML);
                  out.flush();
                }else {
                  boolean saved = save(shortResource, longResource);
                  if(saved) {
                    out.println("HTTP/1.1 201 OK");
                    out.println("Server: Java HTTP Server/Shortner : 1.0");
                    out.println("Date: " + new Date());
                    out.println("Content-type: text/html");
                    out.println("Content-length: 53");
                    out.println();
                    out.println(REDIRECT_RECORDED_HTML);
                    out.flush();
                  } else {
                    out.println("HTTP/1.1 500 Internal Server Error");
                    out.println("Server: Java HTTP Server/Shortner : 1.0");
                    out.println("Date: " + new Date());
                    out.println("Content-type: text/html");
                    out.println("Content-length: 67");
                    out.println();
                    out.println(INTERNAL_SERVER_ERROR_HTML);
                    out.flush();
                  }
                }
              }
            } else {
              Pattern pget = Pattern.compile("^GET\\s+/(\\S+)\\s+(\\S+)$");
              Matcher mget = pget.matcher(input);
              if (mget.matches()) {
                String shortResource = mget.group(1);
                String httpVersion = mget.group(2);

                String longResource = find(shortResource);
                if (longResource != null) {
                  out.println("HTTP/1.1 307 Temporary Redirect");
                  out.println("Location: " + longResource);
                  out.println("Server: Java HTTP Server/Shortner : 1.0");
                  out.println("Date: " + new Date());
                  out.println("Content-type: text/html");
                  out.println("Content-length: 120");
                  out.println();
                  out.println(REDIRECT_HTML);
                  out.flush();
                } else {
                  out.println("HTTP/1.1 404 File Not Found");
                  out.println("Server: Java HTTP Server/Shortner : 1.0");
                  out.println("Date: " + new Date());
                  out.println("Content-type: text/html");
                  out.println("Content-length: 55");
                  out.println();
                  out.println(FILE_NOT_FOUND_HTML);
                  out.flush();
                }
              }
            };
          } 
      }
      } catch (Exception e) {
        System.err.println(e);
      } finally {
        try {
         
          in.close();
          out.close();
          connect.close(); // we close socket connection
        } catch (Exception e) {
          System.err.println("Error closing stream : " + e.getMessage());
        }

        if (verbose) {
          System.out.println("Connection closed.\n");
        }
      }
    }

    private static String find(String shortURL) {
      String[] urlPairing = DB.getLongURL(DB_URL, shortURL);
      if (urlPairing == null) {
        consecutive_failures += 1;
        if(consecutive_failures >= consecutive_failure_limit) {
          System.exit(0);
        }
      } else {
        if(consecutive_failures > 0) {
          consecutive_failures -= 1;
        }
      }
      if (urlPairing[1] != "") {
        return urlPairing[1];
      }
      return null;
    }

    private static boolean save(String shortURL, String longURL) {
      boolean saved = DB.write(DB_URL, shortURL, longURL);
      if (saved == false) {
        consecutive_failures += 1;
        if(consecutive_failures >= consecutive_failure_limit) {
          System.exit(0);
        }
      } else {
        if(consecutive_failures > 0) {
          consecutive_failures -= 1;
        }
      }
      return saved;
    }
  }
}