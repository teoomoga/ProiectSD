package GUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import Indexer.FileIndexer;
import Search.SearchEngine;

public class Interface extends JFrame {
    private JTextField searchField;
    private JTextArea resultsArea;
    private JButton searchButton;
    private SearchEngine searchService;
    private JButton selectFolder;

    public Interface() {
        searchService = new SearchEngine();

        setTitle("File Search Engine");
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
        topPanel.setBackground(new Color(230, 230, 230));

        searchField = new JTextField(30);
        searchButton = new JButton("Search");
        selectFolder = new JButton("Select Folder");

        topPanel.add(new JLabel("Keyword:"));
        topPanel.add(searchField);
        topPanel.add(searchButton);
        topPanel.add(selectFolder);

        resultsArea = new JTextArea();
        resultsArea.setEditable(false);
        //resultsArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        resultsArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(resultsArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Results"));

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        ActionListener searchAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                executeSearch();
            }
        };

        selectFolder.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            int returnVal = chooser.showOpenDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                String selectedPath = chooser.getSelectedFile().getAbsolutePath();

                new Thread(() -> {
                    FileIndexer indexer = new FileIndexer();
                    indexer.indexDirectory(selectedPath);
                }).start();
            }
        });

        searchButton.addActionListener(searchAction);
        searchField.addActionListener(searchAction);
    }

    private void executeSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            resultsArea.setText("Please enter a keyword to search.");
            return;
        }

        resultsArea.setText("Searching for: " + query + "...\n");

        String result = searchService.search(query);
        resultsArea.setText(result);

        resultsArea.setCaretPosition(0);
    }
}