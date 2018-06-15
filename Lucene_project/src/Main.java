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
        Directory index = FSDirectory.open(Paths.get("./data/index"));
        IndexReader reader = DirectoryReader.open(index);

        // Check how many docs are in index
        int index_size = reader.numDocs();
        System.out.println("In index are " + index_size + " docs. (In solr are 1050, should be the same!)\n");

        // Create / load documents database
        List<LinkedHashMap<String, List<Pair<String, Integer>>>> document_db = null;
        Map<String, List<String>> category_map = null;
        File f = new File(DatabaseManager.DB_FILE_NAME); // if we have file with database

        if (f.exists() && !f.isDirectory()) {
            // get document_db and category mapping from reader
            // it is limited only to two values
            Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> temp = new DatabaseManager().loadDocDB();

            // List for all document
            document_db = temp.getKey();

            // List of music category
            category_map = temp.getValue();

        } else { // we have no file with database

            // get document_db and category mapping from reader
            // it is limited only to two values

            Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> temp = new DocumentFinder().getDocDB(reader);

            // List for all document
            document_db = temp.getKey();

            // List of music category
            category_map = temp.getValue();

            System.out.println("__________________________________________________");
            new DatabaseManager().saveDocDB(temp);

        }
        if (category_map == null || document_db == null) {
            System.out.println("\n\n----Something goes wrong with create/load documents database----\n\n");
            System.exit(-1);
        }
        System.out.println(">>>> Obtain database and catogry mapping!\n\n");
        System.out.printf(">> Map category size:%d%n", category_map.size());
        new Exporter().exportToCSV(document_db, category_map);
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
