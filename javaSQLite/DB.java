import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.PreparedStatement;


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
		write(url);
		read(url);
	}
	public static void write(String url) {
		Connection conn=null;
		try {
			conn = connect(url);
			/**
				pragma locking_mode=EXCLUSIVE;
				pragma mmap_size = 30000000000;
				pragma temp_store = memory;
			**/
			String sql = """
			 	pragma journal_mode = WAL;
				pragma synchronous = normal;
			""";
 			Statement stmt  = conn.createStatement();
			stmt.executeUpdate(sql);

			String insertSQL = "INSERT INTO urls (shortURL, longURL) VALUES (?, ?)";
			PreparedStatement ps = conn.prepareStatement(insertSQL);
			for(int i=0;i<100000;i++){
				ps.setString(1, "left thing "+i);
				ps.setInt(2, i);
				ps.execute();
			}
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
	public static void read(String url) {
		Connection conn=null;
		try {
			conn = connect(url);
			Statement stmt  = conn.createStatement();
			String sql = "SELECT shortURL, longURL FROM urls";
			ResultSet rs = stmt.executeQuery(sql);
			int count = 0;
			while (rs.next()) {
				count ++;
				// System.out.println( rs.getString("leftside") + "\t" + rs.getInt("rightside") );
			}
			System.out.println(count);
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
	public static String[] getLongURL(String DBurl, String queryURL) {
		Connection conn=null;
		try {
			conn = connect(DBurl);
			Statement stmt  = conn.createStatement();
			String sql = "SELECT shortURL, longURL FROM urls where shortURL = " + queryURL;
			ResultSet rs = stmt.executeQuery(sql);
			int count = 0;
			String[] pairing = new String[2];
			while (rs.next()){
				String shortURL = rs.getString("shortURL");
				String longURL = rs.getString("longURL");
				pairing[0] = shortURL;
				pairing[1] = longURL;
				count++;
			}

			if (count > 1){
				System.out.println("multiple matching shortURLs in DB");
			}

			return pairing;
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
			return null;
		}
	}
}
