package Search;

import DataBase.DatabaseManager;
import java.sql.*;

public class SearchEngine {
    public String search(String keyword) {
        StringBuilder sb = new StringBuilder();
        String sql = "SELECT filename, path, content FROM project WHERE project MATCH ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement p = conn.prepareStatement(sql)) {

            String searchTerms = keyword.trim().replaceAll("\\s+", " AND ");
            p.setString(1, searchTerms);
            ResultSet rs = p.executeQuery();

            while (rs.next()) {
                String fileName = rs.getString("filename");
                String path = rs.getString("path");
                String content = rs.getString("content");

                int count = 0;
                int pos = 0;
                while (count < 3 && pos < content.length()) {
                    int nextline = content.indexOf("\n", pos);
                    if (nextline == -1) {
                        pos = content.length();
                        break;
                    }
                    pos = nextline + 1;
                    count++;
                }
                String lines = content.substring(0, pos).trim();

                int index = content.toLowerCase().indexOf(keyword.toLowerCase().split("\\s+")[0]);

                if (index != -1) {
                    int start = index;
                    while (start > 0 && ".!?\n".indexOf(content.charAt(start - 1)) == -1) {
                        start--;
                    }

                    int end = index + keyword.split("\\s+")[0].length();
                    while (end < content.length() && ".!?\n".indexOf(content.charAt(end)) == -1) {
                        end++;
                    }

                    if (end < content.length()) end++;

                    String preview = content.substring(start, end).trim().replace("\n", " ");

                    sb.append("File: ").append(fileName).append("\n");
                    sb.append("Sentence: ").append(preview).append("\n");
                    sb.append("Path: ").append(path).append("\n");
                    sb.append("Preview: ").append(lines).append("\n\n");
                }
            }

            if (sb.length() == 0) {
                return "No result";
            }

        } catch (SQLException e) {
            return "Error" + e.getMessage();
        }
        return sb.toString();
    }
}


