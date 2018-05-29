import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class Luc {

    public static String TOKENIZER_MODEL = "models/en-token.bin";
    public static String LOCATION_MODEL = "models/en-ner-location.bin";

    public static void main(String[] args) throws IOException, ParseException {

        // Read index from nutch
        Directory index = FSDirectory.open(Paths.get("./data/index"));
        IndexReader reader = DirectoryReader.open(index);

        // Check how many docs are in index
        int index_size = reader.numDocs();
        System.out.println("In index are " + index_size + " docs. (In solr are 1050, should be the same!)\n");


        // List for all document
        List<LinkedHashMap<String, List<String>>> document_db = new ArrayList<>();

        // Loop over every document
        for (int i = 0; i < reader.maxDoc(); i++) {
            if (i==19){ // test on small number of docs
                break;
            }
            // Hash map for every document
            LinkedHashMap<String, List<String>> doc_hashmap = new LinkedHashMap<>();

            // Obtain proper document
            Document doc = reader.document(i);

            // Check what fields are in
            //getAllFields(doc);

            // Create id from iter over loop (next int on every document)
            String doc_id_value = String.valueOf(i);

            ArrayList<String> doc_id_list = new ArrayList<>();
            doc_id_list.add(doc_id_value);

            doc_hashmap.put("doc_id", doc_id_list);

            // Obtain title from document and add to hashmap
            String doc_title_value = doc.get("id");

            // Remove unnecessary data (Link to Category not band or artist)
            if (doc_title_value.startsWith("https://en.wikipedia.org/wiki/Category:")){
                System.out.println("Found 'Category' in "+i+" docID -> skip");
                continue;
            }

            ArrayList<String> doc_title_list = new ArrayList<>();
            doc_title_list.add(doc_title_value);

            doc_hashmap.put("doc_title", doc_title_list);

            // Get content from document
            String content = doc.get("content");

            // Tokenization
            String[] tokens = tokenization(content);

            // Get locations from tokenize content and add to hashmap (sorted by occurence)
            ArrayList<String> doc_locations_list = (ArrayList<String>) locationFinding(tokens);
            doc_hashmap.put("doc_location", doc_locations_list);

            document_db.add(doc_hashmap);
            System.out.println("Processed docID:" + i);
        }

        printDocDB(document_db);


        // Create searcher
        IndexSearcher searcher = new IndexSearcher(reader);

        // Prepare simple query string
        String querystr = "trip-hop";

        StandardAnalyzer analyzer = new StandardAnalyzer();
        Query q = new QueryParser("content", analyzer).parse(querystr);

        // Config number of output
        int hitsPerPage = 5;

        // Search and obtain results
        TopDocs docs = searcher.search(q, hitsPerPage);
        ScoreDoc[] hits = docs.scoreDocs;

        System.out.println("Found " + hits.length + " hits.");
        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);
            System.out.println((i + 1) + ". " + hits[i].score + "\t" + d.get("title") + "\t" + d.get("url"));
        }

    }

    private static String[] tokenization(String text) throws IOException
    {
        File modelFile = new File(TOKENIZER_MODEL);
        TokenizerModel model = new TokenizerModel(modelFile);
        TokenizerME token = new TokenizerME(model);

        return token.tokenize(text);
    }

    private static List<String> locationFinding(String[] tokens) throws IOException{
        File modelfile = new File(LOCATION_MODEL);
        TokenNameFinderModel namemodel = new TokenNameFinderModel(modelfile);
        NameFinderME namefind = new NameFinderME(namemodel);
        Span[] locations = namefind.find(tokens);

        // Map for count occurence of lcoations and then sort it
        Map<String,Integer> locations_map = new HashMap<>();

        for(Span x:locations){
            // New stringbuilder for concatenate location (Ex. South Korea)
            StringBuilder new_location = new StringBuilder();
            for(int j=x.getStart();j<x.getEnd();j++){
                new_location.append(tokens[j]);
            }

            // Cast to String class (functions below require it)
            String new_location_string = new_location.toString();

            // If location in map plus one, else add new entry
            if (locations_map.containsKey(new_location_string)){
                int temp_counter = locations_map.get(new_location_string);
                temp_counter++;
                locations_map.put(new_location_string, temp_counter);
            } else {
                locations_map.put(new_location_string, 1);
            }

        }

        // Create array and sort by value
        Object[] a = locations_map.entrySet().toArray();
        Arrays.sort(a, new Comparator() {
            public int compare(Object o1, Object o2) {
                return ((Map.Entry<String, Integer>) o2).getValue()
                        .compareTo(((Map.Entry<String, Integer>) o1).getValue());
            }
        });

        // Create array of locations in proper order
        // If counter is the same position is like in original text
        List<String> locations_list = new ArrayList<>();
        for (Object e : a) {
            locations_list.add(((Map.Entry<String, Integer>) e).getKey());
        }

        return locations_list;
    }

    private static void printDocDB(List<LinkedHashMap<String, List<String>>> document_db) {
        for (LinkedHashMap<String, List<String>> doc_map : document_db) {
            // System.out.println(Collections.singletonList(doc_map));
            for (Map.Entry<String, List<String>> entry : doc_map.entrySet()) {
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
