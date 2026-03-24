# System Architecture: Local Search Engine (Iteration 1)

## 1. System Context 
The top level of the C4 model defines the system context, representing the entire system as a whole.
* **User:** A person who navigates the local operating system and requires an extremely fast, fluid, and responsive tool to search through the multitude of documents, media files, and binary files stored on their device.
* **Local Search Engine (System):** The central application that takes on the task of indexing content on the user's device, intelligently utilizing filenames, deep textual content, and associated metadata to provide immediate results.

---

## 2. Containers 
The system context comprises a number of containers that function as distinct deployable units.

* **User Interface Application (Frontend Desktop):**
  A graphical interface that should provide a sense of speed, displaying results as the user types and including file previews (such as the first 3 lines of a document) that are absolutely crucial to user satisfaction.
    * **Library:** **Java Swing** (`javax.swing.*`) — the primary UI toolkit.
        * `JFrame` as the main application window.
        * `JTextField` with a `DocumentListener` (from `javax.swing.event`) to fire live search queries on every keystroke.
        * `JList<String>` / `DefaultListModel<String>` to display result entries dynamically.
        * `JTextArea` (read-only, inside a `JScrollPane`) for inline file previews.
        * `SwingWorker<T, V>` to run background search queries off the Event Dispatch Thread (EDT), keeping the UI responsive.
    * **Layout:** `BorderLayout` for the overall frame; `GridBagLayout` or `MigLayout` (third-party, via Maven) for more complex inner panels.

* **Backend Worker:**
  An asynchronous processing unit that accesses local content, filters out unwanted data, and focuses exclusively, in this first iteration, on searching for content within text files.
    * **Concurrency:** `java.util.concurrent` — use `ExecutorService` (e.g., `Executors.newFixedThreadPool(N)`) to parallelise directory crawling and file reading; `Future<?>` / `CompletableFuture<?>` for composing async tasks.
    * **File I/O:** `java.nio.file` — `Files.walkFileTree()` with a custom `SimpleFileVisitor<Path>` for recursive traversal; `Files.readAllLines()` / `Files.lines()` for text extraction; `BasicFileAttributes` / `PosixFileAttributes` for metadata (timestamps, size, permissions).
    * **Filtering:** `java.util.stream` API with `Stream<Path>` to cleanly filter by extension, size caps, or hidden-file status before handing off to the extractor.

* **Database Management System (RDBMS):**
  A container dedicated to storing extracted data, delegating the index formatting part to the preferred DBMS, whose schema will determine exactly how to process the data at query time.
    * **Embedded DB:** SQLite 
    * **JDBC:** `java.sql` — `DriverManager.getConnection()`, `PreparedStatement`, `ResultSet` for type-safe SQL execution.

---

## 3. Components 
Each container comprises a series of components, which are the major structural blocks at the code level.

* **Directory Crawler:**
  Recursively traverses directories and gracefully handles unexpected edge cases, such as file permission problems, infinite symbolic link loops, or database connection timeouts, without crashing.
    * **Implementation:** Subclass `java.nio.file.SimpleFileVisitor<Path>`, overriding `visitFile()` and `visitFileFailed()`.
    * `visitFileFailed()` catches `IOException` (e.g., `AccessDeniedException`) and logs a warning instead of propagating it, ensuring the walk continues.
    * Symbolic link cycles are avoided by passing `FileVisitOption.FOLLOW_LINKS` only when explicitly desired, leveraging the internal cycle-detection built into `Files.walkFileTree()`.
    * **Logging:** **SLF4J** API (`org.slf4j:slf4j-api`) with **Logback** (`ch.qos.logback:logback-classic`) as the backend — replaces `System.err.println` throughout.

* **Content and Metadata Extraction Processor:**
  A strategic component that treats each piece of information as important, preserving all available metadata (e.g., extensions, tags, timestamps) to easily support future use cases.
    * **Text Extraction:** `java.nio.file.Files.lines(Path, Charset)` with `StandardCharsets.UTF_8`; fall back to `StandardCharsets.ISO_8859_1` on `CharacterCodingException` to handle legacy files.
    * **Metadata:** `java.nio.file.attribute.BasicFileAttributes` — `creationTime()`, `lastModifiedTime()`, `size()`; extract file extension via `Path.getFileName().toString()` string splitting.
    * **Persistence (DTO):** Plain Java **record** classes (Java 16+) — e.g., `record FileRecord(String path, String extension, long sizeBytes, Instant lastModified, String contentSnippet)` — for immutable data transfer between the extractor and the DB layer.
    * **Batch Inserts:** Accumulate `FileRecord` objects in a `List<FileRecord>` and flush to the DB via a `PreparedStatement` in a `executeBatch()` loop for performance.

* **SQL Query and Preview Generation Engine:**
  Uses efficiently written SQL queries to ensure that single-word and multi-word searches work flawlessly, using the database's full-text search functions.
    * **Query API:** `java.sql.PreparedStatement` with `?` placeholders to prevent SQL injection; wrap in a `try-with-resources` block for automatic `ResultSet` / `Statement` cleanup.
    * **Full-Text:** Call H2's `FT_SEARCH_DATA(query, limit, offset)` table-valued function for ranked full-text results; fall back to `LIKE` queries for non-indexed columns.
    * **Preview Generation:** After a search hit, open the matched file with `Files.lines()`, collect the first 3 lines via `.limit(3).collect(Collectors.joining("\n"))`, and pass the result to the Swing `JTextArea`.
    * **Pagination:** Use SQL `LIMIT ? OFFSET ?` clauses; expose page-navigation via Swing `JButton` (Previous / Next) bound to an `ActionListener` that increments/decrements an offset counter.

---

## 4. Classes and Code 
Finally, each component comprises a series of specific classes that contain a set of lower-level functions or methods.

* **`SystemConfigurationManager`:**
  This versatile class allows for runtime configuration of the system for essential aspects such as file skip rules, the root directory, and the format of the report generated at the end by the index builder.
    * **Pattern:** Singleton — `private static final SystemConfigurationManager INSTANCE = new SystemConfigurationManager();` with a `public static getInstance()` accessor, ensuring a single shared configuration object.
    * **Storage:** Backed by `java.util.Properties` loaded from a `config.properties` file on the classpath (read via `ClassLoader.getResourceAsStream()`); changes persisted back with `Properties.store()`.
    * **Key properties:** `root.directory`, `skip.extensions` (comma-separated list), `report.format` (`txt` | `csv`), `db.file.path`.
    * **Skip Rules:** Parsed into a `Set<String>` of lower-cased extensions; checked in `Directory Crawler` via a `Set.contains()` O(1) lookup.

* **`IncrementalIndexUpdater`:**
  An advanced class, responsible for long-term system performance, that performs incremental indexing by detecting file changes and updating only the changed records, instead of inefficiently rebuilding the entire database.
    * **Change Detection:** Use `java.nio.file.WatchService` — register a root `Path` with `StandardWatchEventKinds.ENTRY_CREATE`, `ENTRY_MODIFY`, and `ENTRY_DELETE`; poll events in a dedicated daemon thread.
    * **Delta Logic:** On each `WatchEvent<Path>`, compare the file's `lastModifiedTime()` (from `BasicFileAttributes`) against the timestamp stored in the DB. Re-index only if the file is newer.
    * **DB Update:** Issue an `INSERT OR REPLACE` SQL statement via `PreparedStatement`, targeting the record by its absolute path as the primary key — avoiding duplicate rows.
    * **Shutdown Hook:** Register `Runtime.getRuntime().addShutdownHook(new Thread(indexUpdater::stop))` so the `WatchService` is cleanly closed on JVM exit.
