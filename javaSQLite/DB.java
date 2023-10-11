package javaSQLite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import utils.Row;

public class DB {

  private static Connection connect(String url) {
    Connection conn = null;
    try {
      conn = DriverManager.getConnection(url);
    } catch (SQLException e) {
      System.out.println(e.getMessage());
    }
    return conn;
  }

  public static void main(String[] args) {
    String url = args[0];
    write(url, "short.url/abc", "thisisaverylongurl.com/abcdef");
    read(url);
    getLongURL(url, "short.url/abc");
  }

  public static boolean write(String url, String shortURL, String longURL) {
    Connection conn = null;
    try {
      conn = connect(url);
      /**
				pragma locking_mode=EXCLUSIVE;
				pragma mmap_size = 30000000000;
				pragma temp_store = memory;
			**/
      String sql =
        """
			 	pragma journal_mode = WAL;
				pragma synchronous = normal;
			""";
      Statement stmt = conn.createStatement();
      stmt.executeUpdate(sql);

      String updateSQL =
        "INSERT OR REPLACE INTO urls(shortURL, longURL) VALUES (?, ?);";
      PreparedStatement ps = conn.prepareStatement(updateSQL);
      ps.setString(1, shortURL);
      ps.setString(2, longURL);
      ps.execute();

      return true;
    } catch (SQLException e) {
      System.out.println(e.getMessage());
	  return false;
    } finally {
      try {
        if (conn != null) {
          conn.close();
        }
      } catch (SQLException ex) {
        System.out.println(ex.getMessage());
      }
    }
  }

  public static boolean batch_write(String url, HashMap<String, String> urlsToWrite) {
    Connection conn = null;
    try {
      conn = connect(url);
      /**
       pragma locking_mode=EXCLUSIVE;
       pragma mmap_size = 30000000000;
       pragma temp_store = memory;
       **/
      String sql =
              """
                       pragma journal_mode = WAL;
                      pragma synchronous = normal;
                  """;
      Statement stmt = conn.createStatement();
      stmt.executeUpdate(sql);

      Set<String> urlKeySet = urlsToWrite.keySet();
      PreparedStatement ps;

      for (String shortURL : urlKeySet) {
        String longURL = urlsToWrite.get(shortURL);
        String updateSQL =
                "INSERT OR REPLACE INTO urls(shortURL, longURL) VALUES (?, ?);";
        ps = conn.prepareStatement(updateSQL);
        ps.setString(1, shortURL);
        ps.setString(2, longURL);
        ps.addBatch();
        ps.clearParameters();
      }
      ps.execute();

      return true;
    } catch (SQLException e) {
      System.out.println(e.getMessage());
      return false;
    } finally {
      try {
        if (conn != null) {
          conn.close();
        }
      } catch (SQLException ex) {
        System.out.println(ex.getMessage());
      }
    }
  }

  public static ArrayList<Row> read(String url) {
		ArrayList<Row> dump = new ArrayList<Row>();
		try (Connection conn = connect(url)) {
			Statement stmt  = conn.createStatement();
			String sql = "SELECT shortURL, longURL FROM urls;";
			ResultSet rs = stmt.executeQuery(sql);
			int count = 0;
			while (rs.next()) {
				Row rowEntry = new Row(rs.getString("shortURL"), rs.getString("longURL"));
				dump.add(rowEntry);
				count ++;
			}
			System.out.println(count);
      System.out.println(dump.size());
		} catch (SQLException e) {
			System.out.println(e.getMessage());
      return null;
    }
    return dump;

	}

  public static String[] getLongURL(String DBurl, String queryURL) {
    Connection conn = null;
    try {
      conn = connect(DBurl);
      Statement stmt = conn.createStatement();
      String sql = "SELECT shortURL, longURL FROM urls where shortURL = (?);";
      PreparedStatement ps = conn.prepareStatement(sql);
      ps.setString(1, queryURL);
      ResultSet rs = ps.executeQuery();
      int count = 0;
      String[] pairing = new String[2];
	  pairing[0] = "";
	  pairing[1] = "";
      while (rs.next()) {
        String shortURL = rs.getString("shortURL");
        String longURL = rs.getString("longURL");
        pairing[0] = shortURL;
        pairing[1] = longURL;
        count++;
      }

      if (count > 1) {
        System.out.println("multiple matching shortURLs in DB");
      }
      try {
        if (conn != null) {
          conn.close();
        }
      } catch (SQLException ex) {
        System.out.println(ex.getMessage());
      }
      return pairing;
    } catch (SQLException e) {
      System.out.println(e.getMessage());
      return null;
    }
  }

  public static void delete(String DBurl, String queryURL) {
    Connection conn = null;
    try {
      conn = connect(DBurl);
      Statement stmt = conn.createStatement();
      String insertSQL = "DELETE FROM urls WHERE shortURL = (?);";
      PreparedStatement ps = conn.prepareStatement(insertSQL);
      ps.setString(1, queryURL);
      ps.execute();
    } catch (SQLException e) {
      System.out.println(e.getMessage());
    } finally {
      try {
        if (conn != null) {
          conn.close();
        }
      } catch (SQLException ex) {
        System.out.println(ex.getMessage());
      }
    }
  }
}
