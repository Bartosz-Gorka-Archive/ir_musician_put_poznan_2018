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
import java.util.regex.Pattern;

public class Luc {

    private static String TOKENIZER_MODEL = "models/en-token.bin";
    private static String LOCATION_MODEL = "models/en-ner-location.bin";

    public static void main(String[] args) throws IOException, ParseException {

        // Read index from nutch
        Directory index = FSDirectory.open(Paths.get("./data/index"));
        IndexReader reader = DirectoryReader.open(index);

        // Check how many docs are in index
        int index_size = reader.numDocs();
        System.out.println("In index are " + index_size + " docs. (In solr are 1050, should be the same!)\n");


        // List for all document
        List<LinkedHashMap<String, List<String>>> document_db = new ArrayList<>();

        // List of music category
        Map<String, List<String>> category_map = new HashMap<>();
        // Loop over every document
        for (int i = 0; i < reader.maxDoc(); i++) {
            if (i == 19) { // test on small number of docs
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
            if (doc_title_value.startsWith("https://en.wikipedia.org/wiki/Category:")) {
                // Get category map<name, list of part name> from title (url)
                List<String> tmp_cat_list = new ArrayList<>(extractCategory(doc_title_value));
                String tmp_cat_key = String.join("_", tmp_cat_list);
                category_map.put(tmp_cat_key, tmp_cat_list);
                System.out.println("Found 'Category' in " + i + " docID -> skip");
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

            // Get category from tokenize content and add to hashmap (sorted by occurence in category_map -> list)
            ArrayList<String> doc_category_list = (ArrayList<String>) getBestCategory(tokens, category_map);
            doc_hashmap.put("doc_category", doc_category_list);

            // Get time (date) from tokenize content and add to hasmap (sorted by occurence)
            ArrayList<String> doc_times_list = (ArrayList<String>) timeFinding(tokens);
            doc_hashmap.put("doc_time", doc_times_list);

            // Add document hasmap to global list
            document_db.add(doc_hashmap);
            System.out.println("Processed docID:" + i);
        }

        printDocDB(document_db);

        System.out.println(category_map);

        /// Old code to search things -> if unnecessary remove
//        // Create searcher
//        IndexSearcher searcher = new IndexSearcher(reader);
//
//        // Prepare simple query string
//        String querystr = "trip-hop";
//
//        StandardAnalyzer analyzer = new StandardAnalyzer();
//        Query q = new QueryParser("content", analyzer).parse(querystr);
//
//        // Config number of output
//        int hitsPerPage = 5;
//
//        // Search and obtain results
//        TopDocs docs = searcher.search(q, hitsPerPage);
//        ScoreDoc[] hits = docs.scoreDocs;
//
//        System.out.println("Found " + hits.length + " hits.");
//        for (int i = 0; i < hits.length; ++i) {
//            int docId = hits[i].doc;
//            Document d = searcher.doc(docId);
//            System.out.println((i + 1) + ". " + hits[i].score + "\t" + d.get("title") + "\t" + d.get("url"));
//        }

    }


    private static List<String> getBestCategory(String[] tokens, Map<String, List<String>> category_map) {
        // Cast to ArrayList from String[] to count occurence
        ArrayList<String> tokens_list = new ArrayList<>(Arrays.asList(tokens));

        // Structure to handle occurence of every category
        Map<String, Integer> category_occurence = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : category_map.entrySet()) {
            String key = entry.getKey();
            List<String> value_list = entry.getValue();
            // Count occurence of every word from category name and get average from it (if category occur, should whole)
            Integer occurence = 0;
            for (String x : value_list) {
                int occur_x = Collections.frequency(tokens_list, x);
                occurence += occur_x;
            }
            // Divide by size of value list (number of string in category name)
            Integer avg_occurence = occurence / value_list.size();

            // Add to category to hasmap if not equal to zero
            if (avg_occurence != 0) {
                category_occurence.put(key, avg_occurence);
            }
        }

        // Return sorted list from category hashmap
        return getSortedListFromHashMap(category_occurence);
    }

    private static List<String> extractCategory(String text) {
        // Get name of category like array
        String[] tmp_cat = text.split(":");
        String[] tmp_cat2 = tmp_cat[2].split("_");
        List<String> tmp_cat3 = new ArrayList<>(Arrays.asList(tmp_cat2));

        // Remove know names are not category
        tmp_cat3.remove("musical");
        tmp_cat3.remove("music");
        tmp_cat3.remove("groups");
        tmp_cat3.remove("A");

        return tmp_cat3;
    }

    private static String[] tokenization(String text) throws IOException {
        File modelFile = new File(TOKENIZER_MODEL);
        TokenizerModel model = new TokenizerModel(modelFile);
        TokenizerME token = new TokenizerME(model);

        return token.tokenize(text);
    }


    private static List<String> getSortedListFromHashMap(Map<String, Integer> map) {
        // Create array and sort by value
        Object[] a = map.entrySet().toArray();
        Arrays.sort(a, new Comparator() {
            public int compare(Object o1, Object o2) {
                return ((Map.Entry<String, Integer>) o2).getValue()
                        .compareTo(((Map.Entry<String, Integer>) o1).getValue());
            }
        });

        // Create array of locations in proper order
        // If counter is the same position is like in original text
        List<String> list = new ArrayList<>();
        for (Object e : a) {
            list.add(((Map.Entry<String, Integer>) e).getKey());
        }
        return list;
    }

    private static List<String> locationFinding(String[] tokens) throws IOException {
        File modelfile = new File(LOCATION_MODEL);
        TokenNameFinderModel namemodel = new TokenNameFinderModel(modelfile);
        NameFinderME namefind = new NameFinderME(namemodel);
        Span[] locations = namefind.find(tokens);

        // Map for count occurence of lcoations and then sort it
        Map<String, Integer> locations_map = new HashMap<>();

        // Get string from Span[]
        String[] locations_string = Span.spansToStrings(locations, tokens);
        ArrayList<String> locations_list = new ArrayList<>(Arrays.asList(locations_string));

        for (String new_location_string : locations_list) {

            // If location in map plus one, else add new entry
            if (locations_map.containsKey(new_location_string)) {
                int temp_counter = locations_map.get(new_location_string);
                temp_counter++;
                locations_map.put(new_location_string, temp_counter);
            } else {
                locations_map.put(new_location_string, 1);
            }
        }

        return getSortedListFromHashMap(locations_map);
    }

    private static List<String> timeFinding(String[] tokens) throws IOException {
        // Cast to ArrayList from String[] to count occurence
        ArrayList<String> tokens_list = new ArrayList<>(Arrays.asList(tokens));

        // Structure to handle occurence of every category
        Map<String, Integer> date_occurence_map = new HashMap<>();

        // Create regex for date (XXXX and 'XX)
        Pattern p_long = Pattern.compile("\\d{4}"); // Ex. 1998
        Pattern p_short = Pattern.compile("'\\d{2}"); // Ex. '98

        for (String s : tokens_list) {
            // Obtain date
            String new_s = null;
            if (p_long.matcher(s).matches()) {
                new_s = s;
            } else if (p_short.matcher(s).matches()) {
                new_s = s.replace("'", "19"); // Replace apostrof with 19 ('68 > 1968)
            }
            // Add proper value to hashmap if new_s is not null
            if (new_s != null) {
                // If location in map plus one, else add new entry
                if (date_occurence_map.containsKey(new_s)) {
                    int temp_counter = date_occurence_map.get(new_s);
                    temp_counter++;
                    date_occurence_map.put(new_s, temp_counter);
                } else {
                    date_occurence_map.put(new_s, 1);
                }
            }

        }
        return getSortedListFromHashMap(date_occurence_map);
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
