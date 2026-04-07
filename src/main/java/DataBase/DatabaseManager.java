package DataBase;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.*;

public class DatabaseManager {
    private static final String URL =  "jdbc:sqlite:project_sd.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void create (){
        String sql = "CREATE virtual TABLE IF NOT EXISTS project using fts5(" + "path UNINDEXED, "
                    +"filename, " + "content, " + "last_modified UNINDEXED" + ");";

        try (Connection connection = getConnection(); Statement st = connection.createStatement()){
            st.execute(sql);
            System.out.println(("Database created successfully"));
        } catch (SQLException e){
            System.err.println(e.getMessage());
        }
    }
}
