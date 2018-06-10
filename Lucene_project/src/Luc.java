import javafx.util.Pair;
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

import java.io.*;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public class Luc {

    private static String TOKENIZER_MODEL = "models/en-token.bin";
    private static String LOCATION_MODEL = "models/en-ner-location.bin";
    private static String NAME_MODEL = "models/en-ner-person.bin";
    private static String DB_FILE_NAME = "results/doc_db.ser";

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
        File f = new File(DB_FILE_NAME); // if we have file with database

        if (f.exists() && !f.isDirectory()) {
            // get document_db and category mapping from reader
            // it is limited only to two values
            Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> temp = loadDocDB(DB_FILE_NAME);

            // List for all document
            document_db = temp.getKey();

            // List of music category
            category_map = temp.getValue();

        } else { // we have no file with database

            // get document_db and category mapping from reader
            // it is limited only to two values
            Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> temp = getDocDB(reader);

            // List for all document
            document_db = temp.getKey();

            // List of music category
            category_map = temp.getValue();

            System.out.println("__________________________________________________");
            saveDocDB(temp, DB_FILE_NAME);

        }
        if (category_map == null || document_db == null) {
            System.out.println("\n\n----Something goes wrong with create/load documents database----\n\n");
            System.exit(-1);
        }
        System.out.println(">>>> Obtain database and catogry mapping!\n\n");
        System.out.printf(">> Map category size:%d%n", category_map.size());
        exportToCSV(document_db, category_map);

    }


    private static void exportToCSV(List<LinkedHashMap<String, List<Pair<String, Integer>>>> document_db, Map<String, List<String>> category_map) throws FileNotFoundException {
        // One file for every gropus
        String file_csv_group_cat = "results/group_cat/group_cat.csv";
        File file_group_cat = new File(file_csv_group_cat);
        file_group_cat.getParentFile().mkdirs();
        PrintWriter pw_group_cat = new PrintWriter(file_group_cat);
        StringBuilder sb_group_cat = new StringBuilder();
        // Header in csv file
        sb_group_cat.append("Group");
        sb_group_cat.append(',');
        sb_group_cat.append("Best Category");


        // List of wrong word in category
        List<String> bad_word_name = new ArrayList<>();
        bad_word_name.add("Tools What");
        bad_word_name.add("Permanent");
        bad_word_name.add("Page");
        bad_word_name.add("The");
        bad_word_name.add("Music");
        bad_word_name.add("In");
        bad_word_name.add("Retrieved");
        bad_word_name.add("American");

        Map<String,Map<String, Integer>> cat_loc = new HashMap<>();
        Map<String,Map<String, Integer>> cat_time = new HashMap<>();
        Map<String,Map<String, Integer>> cat_names = new HashMap<>();
        Map<String, Integer> global_name = new HashMap<>();

        for (LinkedHashMap<String, List<Pair<String, Integer>>> x : document_db) {
            for (Map.Entry<String, List<Pair<String, Integer>>> y : x.entrySet()) {
                String key = y.getKey();
                List<Pair<String, Integer>> value = y.getValue();


                // group_name -> category
                if (key.equals("doc_title")) {
                    String group_name = URLDecoder.decode(value.get(0).getKey().substring(29 + 1));
                    String group_best_cat = null;
                    try {
                        group_best_cat = x.get("doc_category").get(0).getKey();
                    } catch (IndexOutOfBoundsException e) {
                        System.out.println("+++++++++++++++++++++++++++++++++++++++++++++"+group_name);
                        group_best_cat = "___NONE___";
                    }
                    sb_group_cat.append('\n');
                    sb_group_cat.append(group_name);
                    sb_group_cat.append(",");
                    sb_group_cat.append(group_best_cat);
                }

                // category -> location
                if (key.equals("doc_category")) {
                    for (Pair<String, Integer> cat : value) {
                        String cat_name = cat.getKey();

                        // Get every location with occurence
                        for (Pair<String, Integer> loc : x.get("doc_location")) {
                            String location = loc.getKey();
                            if(!location.equals("Random")) {
                                Integer occurence = loc.getValue();
                                if (cat_loc.containsKey(cat_name)) { // has category inside
                                    // If location in map plus one, else add new entry
                                    if (cat_loc.get(cat_name).containsKey(location)) { // has location inside
                                        int temp_counter = cat_loc.get(cat_name).get(location);
                                        temp_counter++;
                                        cat_loc.get(cat_name).put(location, temp_counter);
                                    } else { // no location inside
                                        cat_loc.get(cat_name).put(location, occurence);
                                    }
                                } else { // no category inside
                                    Map<String, Integer> tmp2 = new HashMap<>();
                                    tmp2.put(location, occurence);
                                    cat_loc.put(cat_name, tmp2);
                                }
                            }
                        }

                        // Get every time with occurence
                        for (Pair<String, Integer> tim : x.get("doc_time")) {
                            String time = tim.getKey();
                            Integer occurence = tim.getValue();
                            if (cat_time.containsKey(cat_name)) { // has category inside
                                // If time in map plus one, else add new entry
                                if (cat_time.get(cat_name).containsKey(time)) { // has time inside
                                    int temp_counter = cat_time.get(cat_name).get(time);
                                    temp_counter++;
                                    cat_time.get(cat_name).put(time, temp_counter);
                                } else { // no time inside
                                    cat_time.get(cat_name).put(time, occurence);
                                }
                            } else { // no category inside
                                Map<String, Integer> tmp2 = new HashMap<>();
                                tmp2.put(time, occurence);
                                cat_time.put(cat_name, tmp2);
                            }
                        }

                        // Get every name with occurence (with no bad word from name list)
                        for (Pair<String, Integer> names : x.get("doc_name")) {
                            String name = names.getKey();
                            Integer occurence = names.getValue();
                            ArrayList<String> tmpList = new ArrayList<>();
                            tmpList.add(name);
                            if(Collections.disjoint(tmpList,bad_word_name)) {
                                if (cat_names.containsKey(cat_name)) { // has category inside
                                    // If time in map plus one, else add new entry
                                    if (cat_names.get(cat_name).containsKey(name)) { // has time inside
                                        int temp_counter = cat_names.get(cat_name).get(name);
                                        temp_counter++;
                                        cat_names.get(cat_name).put(name, temp_counter);
                                    } else { // no time inside
                                        cat_names.get(cat_name).put(name, occurence);
                                    }
                                } else { // no category inside
                                    Map<String, Integer> tmp2 = new HashMap<>();
                                    tmp2.put(name, occurence);
                                    cat_names.put(cat_name, tmp2);
                                }

                                // for global occurence
                                if (global_name.containsKey(name)){
                                    int temp_counter = global_name.get(name);
                                    temp_counter++;
                                    global_name.put(name, temp_counter);
                                } else {
                                    global_name.put(name, occurence);
                                }
                            }
                        }
                    }
                }
            }
        }
        pw_group_cat.write(sb_group_cat.toString());
        pw_group_cat.close();
        System.out.println("\n>>> Save to " + file_csv_group_cat+"\n");

        mapExportCSV(cat_loc,"cat_loc","Location");
        mapExportCSV(cat_time,"cat_time","Year");
        mapExportCSV(cat_names,"cat_name","Name");
        singleMapExportCSV(global_name,"global_name","Name");

    }

    private static void singleMapExportCSV(Map<String,Integer> map, String name_prefix,String field_name) throws FileNotFoundException {
        String file_csv = "results/"+name_prefix+"/"+name_prefix+".csv";
        File file = new File(file_csv);
        file.getParentFile().mkdirs();
        PrintWriter pw = new PrintWriter(file);
        StringBuilder sb = new StringBuilder();
        // Header in csv file
        sb.append(field_name);
        sb.append(',');
        sb.append("Occur");

        int counter = 1;
        int max_counter = 10; // firs 10 occurence (max)
        Map<String,Integer> sorted_value = getSortedMap(map);
        for (Map.Entry<String, Integer> y: sorted_value.entrySet()) {
            if(counter > max_counter){
                break;
            }
            if(y.getValue() > 1) { //if something is only 1 time it is too low for us
                String tmp_key = y.getKey();
                tmp_key = tmp_key.replaceAll(",", "");
                sb.append("\n");
                sb.append(String.format("{%s}", tmp_key));
                sb.append(",");
                sb.append(y.getValue().toString());
                counter++;
            }

        }

        pw.write(sb.toString());
        pw.close();
        System.out.println(">>> Save to " + file_csv);
    }
    private static void mapExportCSV(Map<String,Map<String,Integer>> map,String name_prefix,String field_name) throws FileNotFoundException {

        for (Map.Entry<String,Map<String,Integer>> x: map.entrySet()) {
            // One file for every groups
            String key = x.getKey();
            Map<String,Integer> value = x.getValue();

            String file_csv = "results/"+name_prefix+"/"+name_prefix+"_"+key+".csv";
            File file = new File(file_csv);
            file.getParentFile().mkdirs();
            PrintWriter pw = new PrintWriter(file);
            StringBuilder sb = new StringBuilder();
            // Header in csv file
            sb.append(field_name);
            sb.append(',');
            sb.append("Occur");

            int counter = 1;
            int max_counter = 10; // firs 10 occurence (max)
            Map<String,Integer> sorted_value = getSortedMap(value);
            for (Map.Entry<String, Integer> y: sorted_value.entrySet()) {
                if(counter > max_counter){
                    break;
                }
                if(y.getValue() > 1) { //if something is only 1 time it is too low for us
                    String tmp_key = y.getKey();
                    tmp_key = tmp_key.replaceAll(",", "");
                    sb.append("\n");
                    sb.append(String.format("{%s}", tmp_key));
                    sb.append(",");
                    sb.append(y.getValue().toString());
                    counter++;
                }

            }

            pw.write(sb.toString());
            pw.close();
            System.out.println(">>> Save to " + file_csv);
        }
    }
    private static Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> getDocDB(IndexReader reader) throws IOException {

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

    private static void saveDocDB(Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> document_db_cat_map, String file_name) {
        try {
            File f = new File(file_name);
            f.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(file_name);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(document_db_cat_map);
            oos.close();
            fos.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        System.out.println("\n>>> Saved DB\n");
    }

    private static Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> loadDocDB(String file_name) {
        Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> document_db_cat_map = new Pair<>(null, null);
        try {
            FileInputStream fis = new FileInputStream(file_name);
            ObjectInputStream ois = new ObjectInputStream(fis);
            document_db_cat_map = (Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>>) ois.readObject();
            ois.close();
            fis.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (ClassNotFoundException c) {
            System.out.println("Class not found");
            c.printStackTrace();
        }
        System.out.println("\n>>> Loaded DB\n");
        return document_db_cat_map;
    }

    private static List<Pair<String, Integer>> getBestCategory(String[] tokens, Map<String, List<String>> category_map) {
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


    private static List<Pair<String, Integer>> getSortedListFromHashMap(Map<String, Integer> map) {
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

    private static Map<String, Integer> getSortedMap(Map<String, Integer> map) {
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
        Map<String, Integer> sorted_map = new LinkedHashMap<>();
        for (Object e : a) {
            sorted_map.put(((Map.Entry<String, Integer>) e).getKey(), ((Map.Entry<String, Integer>) e).getValue());
        }
        return sorted_map;
    }

    private static List<Pair<String, Integer>> nameFinding(String[] tokens) throws IOException {
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

    private static List<Pair<String, Integer>> locationFinding(String[] tokens) throws IOException {
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



    private static List<Pair<String, Integer>> timeFinding(String[] tokens) throws IOException {
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
