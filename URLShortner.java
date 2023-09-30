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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.http.*;
import java.net.URI;
import java.time.Duration;



public class URLShortner {

  static final File WEB_ROOT = new File(".");
  static final String DEFAULT_FILE = "index.html";
  static final String FILE_NOT_FOUND = "404.html";
  static final String METHOD_NOT_SUPPORTED = "not_supported.html";
  static final String REDIRECT_RECORDED = "redirect_recorded.html";
  static final String REDIRECT = "redirect.html";
  static final String NOT_FOUND = "notfound.html";
  static final String DATABASE = "database.txt";
  // port to listen connection
  static final int PORT = 59958;

  public static String PARTITION_1_NAME = "part1";
  public static String PARTITION_1_BACKUP_HOST = "dh2026pc12:59958/";

  public static String PARTITION_2_NAME = "part2";
  public static String PARTITION_2_BACKUP_HOST = "dh2026pc12:59958/";

  // verbose mode
  static final boolean verbose = true;

  public static void main(String[] args) {
    try {
      ServerSocket serverConnect = new ServerSocket(PORT);
      System.out.println(
        "Server started.\nListening for connections on port : " +
        PORT +
        " ...\n"
      );

      // we listen until user halts server execution
      while (true) {
        if (verbose) {
          System.out.println("Connecton opened. (" + new Date() + ")");
          System.out.println(
            "PARTITION 1 NAME, BACKUP_HOST: " +
            PARTITION_1_NAME +
            " " +
            PARTITION_1_BACKUP_HOST
          );
          System.out.println(
            "PARTITION 2 NAME, BACKUP_HOST: " +
            PARTITION_2_NAME +
            " " +
            PARTITION_2_BACKUP_HOST
          );
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

        String input = in.readLine();

        if (verbose) System.out.println("first line: " + input);

        Pattern setpartitionput = Pattern.compile(
          "^PUT\\s+/set-partition\\?id=(\\S+)\\?name=(\\S+)&host=(\\S+)\\s+(\\S+)$"
        );
        Matcher setpartitionmput = setpartitionput.matcher(input);
        if (setpartitionmput.matches()) {
          System.out.println("SET PARTITION MATCH");
          String partitionID = setpartitionmput.group(1);
          String partitionName = setpartitionmput.group(2);
          String host = setpartitionmput.group(3);
          String httpVersion = setpartitionmput.group(4);
          if (Integer.parseInt(partitionID) == 1) {
            PARTITION_1_NAME = partitionName;
            PARTITION_1_BACKUP_HOST = host;
          } else if (Integer.parseInt(partitionID) == 2) {
            PARTITION_2_NAME = partitionName;
            PARTITION_2_BACKUP_HOST = host;
          }
          out.println("HTTP/1.1 200 OK");
          out.println();
          out.flush();
          return;
        }

        Pattern setbackupput = Pattern.compile(
          "^PUT\\s+/set-backup\\?id=(\\S+)\\?short=(\\S+)&long=(\\S+)\\s+(\\S+)$"
        );
        Matcher setbackupmput = setbackupput.matcher(input);
        if (setbackupmput.matches()) {
          String partitionID = setbackupmput.group(1);
          String shortResource = setbackupmput.group(2);
          String longResource = setbackupmput.group(3);
          String httpVersion = setbackupmput.group(4);
          if (Integer.parseInt(partitionID) == 1) {
            save(shortResource, longResource); //TODO: SAVE TO CORRECT DB
          } else if (Integer.parseInt(partitionID) == 2) {
            save(shortResource, longResource);
          }
          out.println("HTTP/1.1 200 OK");
          out.println();
          out.flush();
          return;
        }

        Pattern pput = Pattern.compile(
          "^PUT\\s+/\\?short=(\\S+)&long=(\\S+)\\s+(\\S+)$"
        );
        Matcher mput = pput.matcher(input);
        if (mput.matches()) {
          String shortResource = mput.group(1);
          String longResource = mput.group(2);
          String httpVersion = mput.group(3);

          save(shortResource, longResource);

          HttpClient client = HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
          HttpRequest req = HttpRequest
            .newBuilder()
            .uri(URI.create(PARTITION_1_BACKUP_HOST + "/?short=test&long=https://www.google.ca/"))
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build();
          client.send(req, HttpResponse.BodyHandlers.ofString()).body();

          File file = new File(WEB_ROOT, REDIRECT_RECORDED);
          int fileLength = (int) file.length();
          String contentMimeType = "text/html";
          //read content to return to client
          byte[] fileData = readFileData(file, fileLength);

          out.println("HTTP/1.1 200 OK");
          out.println("Server: Java HTTP Server/Shortner : 1.0");
          out.println("Date: " + new Date());
          out.println("Content-type: " + contentMimeType);
          out.println("Content-length: " + fileLength);
          out.println();
          out.flush();

          dataOut.write(fileData, 0, fileLength);
          dataOut.flush();
        } else {
          Pattern pget = Pattern.compile("^(\\S+)\\s+/(\\S+)\\s+(\\S+)$");
          Matcher mget = pget.matcher(input);
          if (mget.matches()) {
            String method = mget.group(1);
            String shortResource = mget.group(2);
            String httpVersion = mget.group(3);

            String longResource = find(shortResource);
            if (longResource != null) {
              File file = new File(WEB_ROOT, REDIRECT);
              int fileLength = (int) file.length();
              String contentMimeType = "text/html";

              //read content to return to client
              byte[] fileData = readFileData(file, fileLength);

              // out.println("HTTP/1.1 301 Moved Permanently");
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
      } catch (Exception e) {
        System.err.println("Server error " + e.getMessage());
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
      String longURL = null;
      try {
        File file = new File(DATABASE);
        FileReader fileReader = new FileReader(file);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
          String[] map = line.split("\t");
          if (map[0].equals(shortURL)) {
            longURL = map[1];
            break;
          }
        }
        fileReader.close();
      } catch (IOException e) {}
      return longURL;
    }

    private static void save(String shortURL, String longURL) {
      try {
        File file = new File(DATABASE);
        FileWriter fw = new FileWriter(file, true);
        BufferedWriter bw = new BufferedWriter(fw);
        PrintWriter pw = new PrintWriter(fw);
        pw.println(shortURL + "\t" + longURL);
        pw.close();
      } catch (IOException e) {}
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
