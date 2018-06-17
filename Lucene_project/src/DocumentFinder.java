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

    private static final String TOKENIZER_MODEL = "models/en-token.bin";
    private static final String LOCATION_MODEL = "models/en-ner-location.bin";
    private static final String NAME_MODEL = "models/en-ner-person.bin";

    public Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> createDocumentsDb(IndexReader reader) throws IOException {
        // List of music category
        Map<String, List<String>> categoriesMap = findCategories(reader);
        System.out.println(categoriesMap.size());

        System.out.println("\n>>> Classifying documents!\n");
        // List for all document
        List<LinkedHashMap<String, List<Pair<String, Integer>>>> documentsDb = classifyDocuments(reader, categoriesMap);

        return new Pair<>(documentsDb, categoriesMap);
    }

    private Map<String, List<String>> findCategories(IndexReader reader) throws IOException {
        System.out.println("\n>>> Finding category!\n");
        Map<String, List<String>> categoriesMap = new HashMap<>();
        // List of wrong word in category
        List<String> stopWords = new ArrayList<>();
        stopWords.add("by");
        stopWords.add("genre");
        stopWords.add("navigational");
        stopWords.add("(genre)");
        stopWords.add("musicians");
        stopWords.add("nationality");
        stopWords.add("body");
        // First loop to obtain all category
        for (int i = 0; i < reader.maxDoc(); i++) {
            // Read document from index
            Document document = reader.document(i);
            // Obtain title from document and add to hashmap
            String title = document.get("id");
            // Remove unnecessary data (Link to Category not band or artist)
            if (title.startsWith("https://en.wikipedia.org/wiki/Category:")) {
                // Get category map<name, list of part name> from title (url)
                List<String> categoriesList = new ArrayList<>(extractCategories(title));
                if (i < 20 && Collections.disjoint(categoriesList, stopWords)) { // cut to first 20 documents because rest of category are subcategory of this
                    String categoryKey = String.join("_", categoriesList);
                    categoriesMap.put(categoryKey, categoriesList);
                    System.out.println("Found 'Category' in " + i + " docID, with name: " + categoryKey);
                }

            }
        }
        return categoriesMap;
    }

    private List<String> extractCategories(String text) {
        // Get name of category like array
        List<String> categories = new ArrayList<>(Arrays.asList(text.split(":")[2].split("_")));
        // Remove know names are not category
        categories.remove("musical");
        categories.remove("music");
        categories.remove("groups");
        categories.remove("A");
        return categories;
    }

    private List<LinkedHashMap<String, List<Pair<String, Integer>>>> classifyDocuments(IndexReader reader, Map<String, List<String>> categoriesMap) throws IOException {
        List<LinkedHashMap<String, List<Pair<String, Integer>>>> documentsDb = new ArrayList<>();
        for (int i = 0; i < reader.maxDoc(); i++) {

            // To not collect all documents (for test)
//            if (i == 19) { // test on small number of docs
//              break;
//            }

            // Hash map for every document
            LinkedHashMap<String, List<Pair<String, Integer>>> documentHashmap = new LinkedHashMap<>();

            // Obtain proper document
            Document document = reader.document(i);

            // Check what fields are in
//            getAllFields(document);

            // Create id from iter over loop (next int on every document)
            ArrayList<Pair<String, Integer>> idsOfDocuments = new ArrayList<>();
            idsOfDocuments.add(new Pair<>(i + "", 1));

            documentHashmap.put("doc_id", idsOfDocuments);

            // Obtain title from document and add to hashmap
            String title = document.get("id");

            // Remove unnecessary data (Link to Category not band or artist)
            if (title.startsWith("https://en.wikipedia.org/wiki/Category:")) {
                System.out.println("Found 'Category' in " + i + " docID -> skip");
                continue;
            }

            ArrayList<Pair<String, Integer>> titlesOfDocuments = new ArrayList<>();
            titlesOfDocuments.add(new Pair<>(title, 1));
            documentHashmap.put("doc_title", titlesOfDocuments);

            // Get content from document
            String content = document.get("content");

            // Tokenization
            String[] tokens = tokenization(content);

            // Get locations from tokenize content and add to hashmap (sorted by occurence)
            ArrayList<Pair<String, Integer>> locationsOfDocuments = (ArrayList<Pair<String, Integer>>) findLocations(tokens);
            documentHashmap.put("doc_location", locationsOfDocuments);

            // Get category from tokenize content and add to hashmap (sorted by occurence in categoriesMap -> list)
            ArrayList<Pair<String, Integer>> categoriesOfDocuments = (ArrayList<Pair<String, Integer>>) getBestCategories(tokens, categoriesMap);
            documentHashmap.put("doc_category", categoriesOfDocuments);

            // Get time (date) from tokenize content and add to hasmap (sorted by occurence)
            ArrayList<Pair<String, Integer>> datesOfDocuments = (ArrayList<Pair<String, Integer>>) findDates(tokens);
            documentHashmap.put("doc_time", datesOfDocuments);

            // Get name from tokenize content and add to hasmap (sorted by occurence)
            ArrayList<Pair<String, Integer>> namesOfDocuments = (ArrayList<Pair<String, Integer>>) findNames(tokens);
            documentHashmap.put("doc_name", namesOfDocuments);

            // Add document hasmap to global list
            documentsDb.add(documentHashmap);
            System.out.println("Processed docID:" + i);
        }
        // Second Loop over every document for classify documents
        return documentsDb;
    }

    private List<Pair<String, Integer>> getBestCategories(String[] tokens, Map<String, List<String>> categoriesMap) {
        // Cast to ArrayList from String[] to count occurence
        ArrayList<String> tokensList = new ArrayList<>(Arrays.asList(tokens));

        // Structure to handle occurrence of every category
        Map<String, Integer> categoriesOccurrence = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : categoriesMap.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            // Count occurence of every word from category name and get average from it (if category occur, should whole)
            int entryFrequency = 0;
            for (String value : values) {
                int frequency = Collections.frequency(tokensList, value);
                entryFrequency += frequency;
            }
            // Divide by size of value list (number of string in category name)
            int occurrenceAVG = entryFrequency / values.size();

            // Add to category to hasmap if not equal to zero
            if (occurrenceAVG != 0) {
                categoriesOccurrence.put(key, occurrenceAVG);
            }
        }
        // Return sorted list from category hashmap
        return getSortedListFromHashMap(categoriesOccurrence);
    }

    private String[] tokenization(String text) throws IOException {
        File modelFile = new File(TOKENIZER_MODEL);
        TokenizerModel model = new TokenizerModel(modelFile);
        TokenizerME token = new TokenizerME(model);
        return token.tokenize(text);
    }

    private List<Pair<String, Integer>> getSortedListFromHashMap(Map<String, Integer> map) {
        // Create array and sort by value
        Object[] objects = map.entrySet().toArray();
        Arrays.sort(objects, (Comparator) (o1, o2) -> ((Map.Entry<String, Integer>) o2).getValue()
                .compareTo(((Map.Entry<String, Integer>) o1).getValue()));

        // Create array of locations in proper order
        // If counter is the same position is like in original text
        List<Pair<String, Integer>> list = new ArrayList<>();
        for (Object object : objects) {
            list.add(new Pair<>(((Map.Entry<String, Integer>) object).getKey(), ((Map.Entry<String, Integer>) object).getValue()));
        }
        return list;
    }

    private List<Pair<String, Integer>> findNames(String[] tokens) throws IOException {
        File modelFile = new File(NAME_MODEL);
        TokenNameFinderModel tokenNameFinderModel = new TokenNameFinderModel(modelFile);
        NameFinderME nameFinderME = new NameFinderME(tokenNameFinderModel);
        Span[] names = nameFinderME.find(tokens);

        // Map for count occurence of lcoations and then sort it
        Map<String, Integer> namesMap = new HashMap<>();

        // Get string from Span[]
        ArrayList<String> listOfNames = new ArrayList<>(Arrays.asList(Span.spansToStrings(names, tokens)));

        for (String name : listOfNames) {
            // If location in map plus one, else add new entry
            if (namesMap.containsKey(name)) {
                int counter = namesMap.get(name);
                namesMap.put(name, ++counter);
            } else {
                namesMap.put(name, 1);
            }
        }
        return getSortedListFromHashMap(namesMap);
    }

    private List<Pair<String, Integer>> findLocations(String[] tokens) throws IOException {
        File modelFile = new File(LOCATION_MODEL);
        TokenNameFinderModel tokenNameFinderModel = new TokenNameFinderModel(modelFile);
        NameFinderME nameFinderME = new NameFinderME(tokenNameFinderModel);
        Span[] locations = nameFinderME.find(tokens);

        // Map for count occurence of lcoations and then sort it
        Map<String, Integer> locationsMap = new HashMap<>();

        // Get string from Span[]
        ArrayList<String> listOfLocations = new ArrayList<>(Arrays.asList(Span.spansToStrings(locations, tokens)));

        for (String location : listOfLocations) {
            // If location in map plus one, else add new entry
            if (locationsMap.containsKey(location)) {
                int temp_counter = locationsMap.get(location);
                locationsMap.put(location, ++temp_counter);
            } else {
                locationsMap.put(location, 1);
            }
        }
        return getSortedListFromHashMap(locationsMap);
    }

    private List<Pair<String, Integer>> findDates(String[] tokens) throws IOException {
        // Structure to handle occurence of every category
        Map<String, Integer> datesMap = new HashMap<>();

        // Create regex for date (XXXX and 'XX)
        Pattern longDatePattern = Pattern.compile("\\d{4}"); // Ex. 1998
        Pattern shortDatePattern = Pattern.compile("'\\d{2}"); // Ex. '98

        for (String token : tokens) {
            // Obtain date
            String dateString = null;
            if (longDatePattern.matcher(token).matches()) {
                if (token.compareTo("2051") < 0 && token.compareTo("1000") > 0) {
                    dateString = token;
                }
            } else if (shortDatePattern.matcher(token).matches()) {
                dateString = token.replace("'", "19"); // Replace apostrophe with 19 ('68 > 1968)
            }
            // Add proper value to hashmap if new_s is not null
            if (dateString != null) {
                // If location in map plus one, else add new entry
                if (datesMap.containsKey(dateString)) {
                    int temp_counter = datesMap.get(dateString);
                    datesMap.put(dateString, ++temp_counter);
                } else {
                    datesMap.put(dateString, 1);
                }
            }
        }
        return getSortedListFromHashMap(datesMap);
    }
}
