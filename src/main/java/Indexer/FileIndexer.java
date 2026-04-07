package Indexer;

import DataBase.DatabaseManager;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.*;

public class FileIndexer {
    private int filesFound = 0;
    private int filesInserted = 0;
    private int filesUpdated = 0;

    public void indexDirectory(String folderPath) {
        filesFound = 0;
        filesInserted = 0;
        filesUpdated = 0;

        try {
            Files.walk(Paths.get(folderPath))
                    .filter(p -> {
                        try {
                            return Files.isRegularFile(p) && p.toString().endsWith(".txt");
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .forEach(p -> {
                        filesFound++;
                        saveFileToDb(p);
                    });

            cleanup(folderPath);

            System.out.println("Files " + filesFound);
            System.out.println("Files inserted" + filesInserted);
            System.out.println("files updated " + filesUpdated);

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
                        filesUpdated++;
                        System.out.println("Update: " + path.getFileName());
                    }
                } else {
                    insertIntoDb(connection, path, lastModifiedOnDisk);
                    filesInserted++;
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

    public void cleanup(String folderPath) {
        String selectSql = "SELECT path FROM project";
        String deleteSql = "DELETE FROM project WHERE path = ?";

        try (Connection conn = DatabaseManager.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(selectSql)) {

            while (rs.next()) {
                String pathInDb = rs.getString("path");
                if (!Files.exists(Paths.get(pathInDb))) {
                    try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                        ps.setString(1, pathInDb);
                        ps.executeUpdate();
                        System.out.println("Removed: " + pathInDb);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }
}