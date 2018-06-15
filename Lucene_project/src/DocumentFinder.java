import javafx.util.Pair;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class DocumentFinder {

    private static String TOKENIZER_MODEL = "models/en-token.bin";
    private static String LOCATION_MODEL = "models/en-ner-location.bin";
    private static String NAME_MODEL = "models/en-ner-person.bin";

    public Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> getDocDB(IndexReader reader) throws IOException {

        System.out.println("\n>>> Finding category!\n");
        // List of music category
        Map<String, List<String>> category_map = new HashMap<>();

        // List of wrong word in category
        List<String> bad_word = new ArrayList<>();
        bad_word.add("by");
        bad_word.add("genre");
        bad_word.add("navigational");
        bad_word.add("(genre)");
        bad_word.add("musicians");
        bad_word.add("nationality");
        bad_word.add("body");
        // First loop to obtain all category
        for (int i = 0; i < reader.maxDoc(); i++) {
            // Read document from index
            Document doc = reader.document(i);

            // Obtain title from document and add to hashmap
            String doc_title_value = doc.get("id");

            // Remove unnecessary data (Link to Category not band or artist)
            if (doc_title_value.startsWith("https://en.wikipedia.org/wiki/Category:")) {
                // Get category map<name, list of part name> from title (url)
                List<String> tmp_cat_list = new ArrayList<>(extractCategory(doc_title_value));
                if(Collections.disjoint(tmp_cat_list,bad_word) && i < 20){ // cut to first 20 documents because rest of category are subcategory of this
                    String tmp_cat_key = String.join("_", tmp_cat_list);
                    category_map.put(tmp_cat_key, tmp_cat_list);
                    System.out.println("Found 'Category' in " + i + " docID, with name: " + tmp_cat_key);
                }

            }
        }
        System.out.println(category_map.size());


        System.out.println("\n>>> Classifying documents!\n");
        // List for all document
        List<LinkedHashMap<String, List<Pair<String, Integer>>>> document_db = new ArrayList<>();

        // Second Loop over every document for classify documents
        for (int i = 0; i < reader.maxDoc(); i++) {

            // To not collect all documents (for test)
//            if (i == 19) { // test on small number of docs
//              break;
//            }

            // Hash map for every document
            LinkedHashMap<String, List<Pair<String, Integer>>> doc_hashmap = new LinkedHashMap<>();

            // Obtain proper document
            Document doc = reader.document(i);

            // Check what fields are in
//            getAllFields(doc);

            // Create id from iter over loop (next int on every document)
            String doc_id_value = String.valueOf(i);

            ArrayList<Pair<String, Integer>> doc_id_list = new ArrayList<>();
            doc_id_list.add(new Pair<>(doc_id_value, 1));

            doc_hashmap.put("doc_id", doc_id_list);

            // Obtain title from document and add to hashmap
            String doc_title_value = doc.get("id");

            // Remove unnecessary data (Link to Category not band or artist)
            if (doc_title_value.startsWith("https://en.wikipedia.org/wiki/Category:")) {
                System.out.println("Found 'Category' in " + i + " docID -> skip");
                continue;
            }

            ArrayList<Pair<String, Integer>> doc_title_list = new ArrayList<>();
            doc_title_list.add(new Pair<>(doc_title_value, 1));
            doc_hashmap.put("doc_title", doc_title_list);

            // Get content from document
            String content = doc.get("content");

            // Tokenization
            String[] tokens = tokenization(content);

            // Get locations from tokenize content and add to hashmap (sorted by occurence)
            ArrayList<Pair<String, Integer>> doc_locations_list = (ArrayList<Pair<String, Integer>>) locationFinding(tokens);
            doc_hashmap.put("doc_location", doc_locations_list);

            // Get category from tokenize content and add to hashmap (sorted by occurence in category_map -> list)
            ArrayList<Pair<String, Integer>> doc_category_list = (ArrayList<Pair<String, Integer>>) getBestCategory(tokens, category_map);
            doc_hashmap.put("doc_category", doc_category_list);

            // Get time (date) from tokenize content and add to hasmap (sorted by occurence)
            ArrayList<Pair<String, Integer>> doc_times_list = (ArrayList<Pair<String, Integer>>) timeFinding(tokens);
            doc_hashmap.put("doc_time", doc_times_list);

            // Get name from tokenize content and add to hasmap (sorted by occurence)
            ArrayList<Pair<String, Integer>> doc_name_list = (ArrayList<Pair<String, Integer>>) nameFinding(tokens);
            doc_hashmap.put("doc_name", doc_name_list);

            // Add document hasmap to global list
            document_db.add(doc_hashmap);
            System.out.println("Processed docID:" + i);
        }

        return new Pair<>(document_db, category_map);
    }

    private List<Pair<String, Integer>> getBestCategory(String[] tokens, Map<String, List<String>> category_map) {
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

    private List<String> extractCategory(String text) {
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

    private String[] tokenization(String text) throws IOException {
        File modelFile = new File(TOKENIZER_MODEL);
        TokenizerModel model = new TokenizerModel(modelFile);
        TokenizerME token = new TokenizerME(model);

        return token.tokenize(text);
    }

    private List<Pair<String, Integer>> getSortedListFromHashMap(Map<String, Integer> map) {
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
        List<Pair<String, Integer>> list = new ArrayList<>();
        for (Object e : a) {
            list.add(new Pair<>(((Map.Entry<String, Integer>) e).getKey(), ((Map.Entry<String, Integer>) e).getValue()));
        }
        return list;
    }

    private List<Pair<String, Integer>> nameFinding(String[] tokens) throws IOException {
        File modelfile = new File(NAME_MODEL);
        TokenNameFinderModel namemodel = new TokenNameFinderModel(modelfile);
        NameFinderME namefind = new NameFinderME(namemodel);
        Span[] names = namefind.find(tokens);

        // Map for count occurence of lcoations and then sort it
        Map<String, Integer> names_map = new HashMap<>();

        // Get string from Span[]
        String[] names_string = Span.spansToStrings(names, tokens);
        ArrayList<String> names_list = new ArrayList<>(Arrays.asList(names_string));

        for (String new_name_string : names_list) {

            // If location in map plus one, else add new entry
            if (names_map.containsKey(new_name_string)) {
                int temp_counter = names_map.get(new_name_string);
                temp_counter++;
                names_map.put(new_name_string, temp_counter);
            } else {
                names_map.put(new_name_string, 1);
            }
        }

        return getSortedListFromHashMap(names_map);
    }

    private List<Pair<String, Integer>> locationFinding(String[] tokens) throws IOException {
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

    private List<Pair<String, Integer>> timeFinding(String[] tokens) throws IOException {
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
                if(s.compareTo("2051") < 0 && s.compareTo("1000") > 0) {
                    new_s = s;
                }
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
}
