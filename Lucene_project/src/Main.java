import javafx.util.Pair;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws IOException, ParseException {

        // Read index from nutch
        Directory directory = FSDirectory.open(Paths.get("./data/index"));
        IndexReader indexReader = DirectoryReader.open(directory);

        // Check how many docs are in index
        System.out.println("In index are " + indexReader.numDocs() + " docs. (In solr are 1050, should be the same!)\n");

        // Create / load documents database
        List<LinkedHashMap<String, List<Pair<String, Integer>>>> documentsDb;
        Map<String, List<String>> categoryMap;

        DatabaseManager databaseManager = new DatabaseManager();
        Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> tmp;

        if (isFileDBCorrect(new File(DatabaseManager.DB_FILE_NAME))) {
            // get document_db and category mapping from reader
            // it is limited only to two values
            tmp = databaseManager.loadDocumentsDb();
            // List for all document
            documentsDb = tmp.getKey();
            // List of music category
            categoryMap = tmp.getValue();
        } else { // we have no file with database
            // get document_db and category mapping from reader
            // it is limited only to two values
            tmp = new DocumentFinder().createDocumentsDb(indexReader);
            // List for all document
            documentsDb = tmp.getKey();
            // List of music category
            categoryMap = tmp.getValue();
            System.out.println("__________________________________________________");
            databaseManager.saveDocumentsDb(tmp);
        }
        if (categoryMap == null || documentsDb == null) {
            System.out.println("\n\n----Something goes wrong with create/load documents database----\n\n");
            System.exit(-1);
        }
        System.out.println(">>>> Obtain database and category mapping!\n\n");
        System.out.printf(">> Map category size:%d%n", categoryMap.size());
        new Exporter().exportToCSV(documentsDb, categoryMap);
    }

    private static boolean isFileDBCorrect(File databaseFile) {
        return databaseFile.exists() && !databaseFile.isDirectory();
    }

    private static void printDocDB(List<LinkedHashMap<String, List<Pair<String, Integer>>>> document_db) {
        for (LinkedHashMap<String, List<Pair<String, Integer>>> doc_map : document_db) {
            // System.out.println(Collections.singletonList(doc_map));
            for (Map.Entry<String, List<Pair<String, Integer>>> entry : doc_map.entrySet()) {
                System.out.println(entry.getKey() + " = " + entry.getValue());
            }
            // Print new line for readability
            System.out.println();
        }
    }

    private static void getAllFields(Document doc) {
        /* What is in document (probably in every)
            >>> How to obtain all fields
            //
            What inside:
            1. tstamp - timestamp
            2. segment - segment in index
            3. digest - i do not know
            4. boost - i do not know
            5. id - url
            6. title - title of site
            7. url - url
            8. _version_ - version
            9. content - content of site (plain text)
            Ex.:
                [stored<tstamp:1526998186593>,
                stored<segment:20180522154246>,
                stored<digest:c82e9609dc28377483467711972648f6>,
                stored<boost:0.22584188>,
                stored,indexed,tokenized,omitNorms,indexOptions=DOCS<id:https://en.wikipedia.org/wiki/Zuntata>,
                stored,indexed,tokenized<title:Zuntata - Wikipedia>,
                stored,indexed,tokenized<url:https://en.wikipedia.org/wiki/Zuntata>,
                stored<_version_:1601174745829605376>,
                stored,indexed,tokenized<content:Zuntata - Wikipedia Zuntata ....

            */
        List<IndexableField> fields = doc.getFields();
        System.out.println(fields);
    }
}
