package Indexer;

import DataBase.DatabaseManager;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;

public class FileIndexer {

    public void indexDirectory(String folderPath) {
        try {
            Files.walk(Paths.get(folderPath))
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".txt"))
                    .forEach(this::saveFileToDb);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private void saveFileToDb(Path path) {
        try (Connection connection = DatabaseManager.getConnection()) {
            long lastModifiedOnDisk = Files.getLastModifiedTime(path).toMillis();
            String filePath = path.toString();

            String checkSql = "SELECT last_modified FROM project WHERE path = ?";
            try (PreparedStatement p = connection.prepareStatement(checkSql)) {
                p.setString(1, filePath);
                ResultSet rs = p.executeQuery();

                if (rs.next()) {
                    long lastModifiedInDb = rs.getLong("last_modified");

                    if (lastModifiedOnDisk > lastModifiedInDb) {
                        updateDb(connection, path, lastModifiedOnDisk);
                        System.out.println("Update: " + path.getFileName());
                    }
                } else {
                    insertIntoDb(connection, path, lastModifiedOnDisk);
                    System.out.println("Insert " + path.getFileName());
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private void insertIntoDb(Connection conn, Path path, long lastModified) throws Exception {
        String sql = "INSERT INTO project (path, filename, content, last_modified) VALUES (?, ?, ?, ?)";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, path.toString());
            p.setString(2, path.getFileName().toString());
            p.setString(3, Files.readString(path));
            p.setLong(4, lastModified);
            p.executeUpdate();
        }
    }

    private void updateDb(Connection conn, Path path, long lastModified) throws Exception {
        String sql = "UPDATE project SET content = ?, last_modified = ? WHERE path = ?";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, Files.readString(path));
            p.setLong(2, lastModified);
            p.setString(3, path.toString());
            p.executeUpdate();
        }
    }
}