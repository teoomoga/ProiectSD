import DataBase.DatabaseManager;
import Indexer.FileIndexer;

public class    Main {
    public static void main(String[] args) {
        DatabaseManager.create();

        FileIndexer indexer = new FileIndexer();
        indexer.indexDirectory("/Users/teomoga/Documents/SD/ProjectFiles");
    }
}
