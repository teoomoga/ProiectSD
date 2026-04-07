import DataBase.DatabaseManager;
import Indexer.FileIndexer;
import Search.SearchEngine;

public class Main {
    public static void main(String[] args) {
        DatabaseManager.create();

        //FileIndexer indexer = new FileIndexer();
        //indexer.indexDirectory("/Users/teomoga/Documents/SD/ProjectFiles");

        GUI.Interface window = new GUI.Interface();
        window.setVisible(true);
    }
}
