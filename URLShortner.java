import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
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

public class URLShortner {

  static final File WEB_ROOT = new File(".");
  static final String DEFAULT_FILE = "index.html";
  static final String FILE_NOT_FOUND = "404.html";
  static final String METHOD_NOT_SUPPORTED = "not_supported.html";
  static final String REDIRECT_RECORDED = "redirect_recorded.html";
  static final String REDIRECT = "redirect.html";
  static final String NOT_FOUND = "notfound.html";
  static final String BAD_REQUEST = "bad_request.html";
  static final String DATABASE = "database.txt";
  static final String MANIFEST = "./config/manifest";
  static String HOSTNAME = null;
  static String HOSTNAMEPORT = null;
  // port to listen connection
  static final int PORT = 59958;

  public static String PARTITION_1_NAME = "part1";
  public static String PARTITION_1_BACKUP_HOST = "http://dh2026pc12:59958/";
  public static String PARTITION_2_NAME = "part2";
  public static String PARTITION_2_BACKUP_HOST = "http://dh2026pc12:59958/";

  static DB DB = null;
  static final String DB_URL = "jdbc:sqlite:/virtual/daidkara/example.db";

  // verbose mode
  static final boolean verbose = true;

  public static void main(String[] args) {
    try {
      HOSTNAME = InetAddress.getLocalHost().getHostName();
      HOSTNAMEPORT = HOSTNAME + ":" + PORT;
      DB = new DB();
      System.out.println("Attempting to start server on: " + HOSTNAMEPORT);
      ServerSocket serverConnect = new ServerSocket(PORT);
      System.out.println(
        "Server started.\nListening for connections on port : " +
        PORT +
        " ...\n"
      );
      System.out.println("Reading Parition Information from manifest...");
      boolean readSuccess = updateFromManifest();
      if (readSuccess) {
        System.out.println("Sucessfully read partition information");
      } else {
        System.out.println("FAILED TO READ MANIFEST");
      }
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

  private static boolean updateFromManifest() {
    try {
      File file = new File(MANIFEST);
      FileReader fileReader = new FileReader(file);
      BufferedReader bufferedReader = new BufferedReader(fileReader);
      String line;
      String HOSTNAME1 = null;
      String HOSTNAME1PARTITION = null;
      String HOSTNAME2 = null;
      String HOSTNAME2PARTITION = null;
      while ((line = bufferedReader.readLine()) != null) {
        String[] map = line.split(",");
        String partitionNumber = map[0];
        String hostOne = map[1];
        String hostTwo = map[2];
        if (hostOne.equals(HOSTNAMEPORT)) {
          if (HOSTNAME1 != null && !HOSTNAME1.isEmpty()) {
            HOSTNAME2 = hostTwo;
            HOSTNAME2PARTITION = partitionNumber;
          } else {
            HOSTNAME1 = hostTwo;
            HOSTNAME1PARTITION = partitionNumber;
          }
        }
        if (hostTwo.equals(HOSTNAMEPORT)) {
          if (HOSTNAME1 != null && !HOSTNAME1.isEmpty()) {
            HOSTNAME2 = hostOne;
            HOSTNAME2PARTITION = partitionNumber;
          } else {
            HOSTNAME1 = hostOne;
            HOSTNAME1PARTITION = partitionNumber;
          }
        }
      }
      fileReader.close();
      if (
        HOSTNAME1 != null &&
        !HOSTNAME1.isEmpty() &&
        HOSTNAME2 != null &&
        !HOSTNAME2.isEmpty() &&
        HOSTNAME1PARTITION != null &&
        !HOSTNAME1PARTITION.isEmpty() &&
        HOSTNAME2PARTITION != null &&
        !HOSTNAME2PARTITION.isEmpty()
      ) {
        PARTITION_1_NAME = HOSTNAME1PARTITION;
        PARTITION_1_BACKUP_HOST = "http://" + HOSTNAME1 + "/";
        PARTITION_2_NAME = HOSTNAME2PARTITION;
        PARTITION_2_BACKUP_HOST = "http://" + HOSTNAME2 + "/";
        System.out.println("Sucessfully read partition information");
        System.out.println(
          "PARTITION1 NAME,HOST : " +
          PARTITION_1_NAME +
          "," +
          PARTITION_1_BACKUP_HOST
        );
        System.out.println(
          "PARTITION1 NAME,HOST : " +
          PARTITION_2_NAME +
          "," +
          PARTITION_2_BACKUP_HOST
        );
        return true;
      }
      System.out.println("FAILED TO READ MANIFEST");
      return false;
    } catch (IOException e) {
      return false;
    }
  }

  private static class HandleRequestWorker implements Runnable {

    private Socket connectionSocket;

    public HandleRequestWorker(Socket connection) {
      this.connectionSocket = connection;
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
          if (
            line.startsWith("GET") ||
            line.startsWith("PUT") ||
            line.startsWith("DISTRIBUTE")
          ) {
            input = line;
            if (verbose) System.out.println("first line: " + input);

            Pattern distributeput = Pattern.compile(
              "^DISTRIBUTE\\s+/(\\S+)\\s+(\\S+)$"
            );
            Matcher distributemput = distributeput.matcher(input);
            if (distributemput.matches()) {
              String numHosts = distributemput.group(1);
              String httpVersion = distributemput.group(2);
              System.out.println("DISTRIBUTE TO " + numHosts);
              boolean success = updateFromManifest();
              if (success) {
                out.println("HTTP/1.1 200 OK");
              } else {
                out.println("HTTP/1.1 409 Conflict");
              }
              out.println("Server: Java HTTP Server/Shortner : 1.0");
              out.println("Date: " + new Date());
              out.println();
              out.flush();
            }

            Pattern setbackupput = Pattern.compile(
              "^PUT\\s+/set-backup\\?short=(\\S+)&long=(\\S+)\\s+(\\S+)$"
            );
            Matcher setbackupmput = setbackupput.matcher(input);
            if (setbackupmput.matches()) {
              String shortResource = setbackupmput.group(1);
              String longResource = setbackupmput.group(2);
              String httpVersion = setbackupmput.group(3);
              System.out.println(
                "SAVING BACKUP: " + shortResource + " " + longResource
              );
              save(shortResource, longResource);
              out.println("HTTP/1.1 200 OK");
              out.println();
              out.flush();
            }

            Pattern pput = Pattern.compile(
              "^PUT\\s+/\\?short=(\\S+)&long=(\\S+)\\s+(\\S+)$"
            );
            Matcher mput = pput.matcher(input);
            if (mput.matches()) {
              String shortResource = mput.group(1);
              String longResource = mput.group(2);
              String httpVersion = mput.group(3);

              String contentMimeType = null;
              int fileLength = 0;
              byte[] fileData = null;
              if (
                shortResource == null ||
                shortResource.isEmpty() ||
                longResource == null ||
                longResource.isEmpty()
              ) {
                File file = new File(WEB_ROOT, BAD_REQUEST);
                fileLength = (int) file.length();
                contentMimeType = "text/html";
                fileData = readFileData(file, fileLength);
                out.println("HTTP/1.1 400 BAD REQUEST");
              } else {
                save(shortResource, longResource);
                File file = new File(WEB_ROOT, REDIRECT_RECORDED);
                fileLength = (int) file.length();
                contentMimeType = "text/html";
                fileData = readFileData(file, fileLength);
                out.println("HTTP/1.1 201 OK");
              }
              out.println("Server: Java HTTP Server/Shortner : 1.0");
              out.println("Date: " + new Date());
              out.println("Content-type: " + contentMimeType);
              out.println("Content-length: " + fileLength);
              out.println();
              out.flush();
              dataOut.write(fileData, 0, fileLength);
              dataOut.flush();
              // HttpClient client = HttpClient
              //   .newBuilder()
              //   .connectTimeout(Duration.ofSeconds(10))
              //   .build();
              // HttpRequest req = HttpRequest
              //   .newBuilder()
              //   .uri(URI.create(PARTITION_1_BACKUP_HOST + "set-backup?short=test&long=https://www.google.ca/"))
              //   .PUT(HttpRequest.BodyPublishers.noBody())
              //   .build();
              // client.send(req, HttpResponse.BodyHandlers.ofString()).body();
            } else {
              Pattern pget = Pattern.compile("^GET\\s+/(\\S+)\\s+(\\S+)$");
              Matcher mget = pget.matcher(input);
              if (mget.matches()) {
                String shortResource = mget.group(1);
                String httpVersion = mget.group(2);

                String longResource = find(shortResource);
                if (longResource != null) {
                  File file = new File(WEB_ROOT, REDIRECT);
                  int fileLength = (int) file.length();
                  String contentMimeType = "text/html";

                  //read content to return to client
                  byte[] fileData = readFileData(file, fileLength);

                  out.println("HTTP/1.1 307 Temporary Redirect");
                  out.println("Location: " + longResource);
                  out.println("Server: Java HTTP Server/Shortner : 1.0");
                  out.println("Date: " + new Date());
                  out.println("Content-type: " + contentMimeType);
                  out.println("Content-length: " + fileLength);
                  out.println();
                  out.flush();

                  dataOut.write(fileData, 0, fileLength);
                  dataOut.flush();
                } else {
                  File file = new File(WEB_ROOT, FILE_NOT_FOUND);
                  int fileLength = (int) file.length();
                  String content = "text/html";
                  byte[] fileData = readFileData(file, fileLength);

                  out.println("HTTP/1.1 404 File Not Found");
                  out.println("Server: Java HTTP Server/Shortner : 1.0");
                  out.println("Date: " + new Date());
                  out.println("Content-type: " + content);
                  out.println("Content-length: " + fileLength);
                  out.println();
                  out.flush();

                  dataOut.write(fileData, 0, fileLength);
                  dataOut.flush();
                }
              }
            }
            // File file = new File(WEB_ROOT, METHOD_NOT_SUPPORTED);
            // int fileLength = (int) file.length();
            // String contentMimeType = "text/html";

            // //read content to return to client
            // byte[] fileData = readFileData(file, fileLength);

            // out.println("HTTP/1.1 405 METHOD NOT ALLOWED");
            // out.println("Server: Java HTTP Server/Shortner : 1.0");
            // out.println("Date: " + new Date());
            // out.println("Content-type: " + contentMimeType);
            // out.println("Content-length: " + fileLength);
            // out.println();
            // out.flush();

            // dataOut.write(fileData, 0, fileLength);
            // dataOut.flush();
          } else {
            System.out.println("NON MATCHING LINE: " + line);
          }
        }
      } catch (Exception e) {
        System.err.println("Server error " + e.getMessage());
      } finally {
        try {
          System.out.println("CLOSING CONNECTION");
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
      if (urlPairing[1] != null) {
        return urlPairing[1];
      }
      return null;
    }

    private static void save(String shortURL, String longURL) {
      DB.write(DB_URL, shortURL, longURL);
      return;
    }

    private static byte[] readFileData(File file, int fileLength)
      throws IOException {
      FileInputStream fileIn = null;
      byte[] fileData = new byte[fileLength];

      try {
        fileIn = new FileInputStream(file);
        fileIn.read(fileData);
      } finally {
        if (fileIn != null) fileIn.close();
      }

      return fileData;
    }
  }
}
