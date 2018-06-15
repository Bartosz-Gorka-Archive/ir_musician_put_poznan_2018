import javafx.util.Pair;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.util.*;

public class Exporter {

    public void exportToCSV(List<LinkedHashMap<String, List<Pair<String, Integer>>>> documentDb, Map<String, List<String>> categoryMap) throws FileNotFoundException {
        // One file for every gropus
        String filename = "results/group_cat/group_cat.csv";
        File file = new File(filename);
        file.getParentFile().mkdirs();
        PrintWriter printWriter = new PrintWriter(file);
        StringBuilder stringBuilder = new StringBuilder();
        // Header in csv file
        stringBuilder.append("Group");
        stringBuilder.append(',');
        stringBuilder.append("Best Category");

        // List of wrong word in category
        List<String> stopWords = new ArrayList<>();
        stopWords.add("Tools What");
        stopWords.add("Permanent");
        stopWords.add("Page");
        stopWords.add("The");
        stopWords.add("Music");
        stopWords.add("In");
        stopWords.add("Retrieved");
        stopWords.add("American");

        Map<String,Map<String, Integer>> categoryLocations = new HashMap<>();
        Map<String,Map<String, Integer>> categoryDates = new HashMap<>();
        Map<String,Map<String, Integer>> categoryNames = new HashMap<>();
        Map<String, Integer> globalName = new HashMap<>();

        for (LinkedHashMap<String, List<Pair<String, Integer>>> mapsList : documentDb) {
            for (Map.Entry<String, List<Pair<String, Integer>>> entry : mapsList.entrySet()) {
                String key = entry.getKey();
                List<Pair<String, Integer>> value = entry.getValue();

                // group_name -> category
                if (key.equals("doc_title")) {
                    String groupName = URLDecoder.decode(value.get(0).getKey().substring(29 + 1));
                    String groupBestCategory = null;
                    try {
                        groupBestCategory = mapsList.get("doc_category").get(0).getKey();
                    } catch (IndexOutOfBoundsException e) {
                        System.out.println("+++++++++++++++++++++++++++++++++++++++++++++"+groupName);
                        groupBestCategory = "___NONE___";
                    }
                    stringBuilder.append('\n');
                    stringBuilder.append(groupName);
                    stringBuilder.append(",");
                    stringBuilder.append(groupBestCategory);
                }
                // category -> location
                if (key.equals("doc_category")) {
                    for (Pair<String, Integer> category : value) {
                        String categoryName = category.getKey();

                        // Get every location with occurrence
                        for (Pair<String, Integer> documentLocation : mapsList.get("doc_location")) {
                            String location = documentLocation.getKey();
                            if(!location.equals("Random")) {
                                Integer occurrence = documentLocation.getValue();
                                if (categoryLocations.containsKey(categoryName)) { // has category inside
                                    // If location in map plus one, else add new entry
                                    if (categoryLocations.get(categoryName).containsKey(location)) { // has location inside
                                        int counter = categoryLocations.get(categoryName).get(location);
                                        categoryLocations.get(categoryName).put(location, ++counter);
                                    } else { // no location inside
                                        categoryLocations.get(categoryName).put(location, occurrence);
                                    }
                                } else { // no category inside
                                    Map<String, Integer> newCategory = new HashMap<>();
                                    newCategory.put(location, occurrence);
                                    categoryLocations.put(categoryName, newCategory);
                                }
                            }
                        }

                        // Get every time with occurrence
                        for (Pair<String, Integer> documentDate : mapsList.get("doc_time")) {
                            String dateString = documentDate.getKey();
                            Integer occurrence = documentDate.getValue();
                            if (categoryDates.containsKey(categoryName)) { // has category inside
                                // If time in map plus one, else add new entry
                                if (categoryDates.get(categoryName).containsKey(dateString)) { // has time inside
                                    int counter = categoryDates.get(categoryName).get(dateString);
                                    categoryDates.get(categoryName).put(dateString, ++counter);
                                } else { // no time inside
                                    categoryDates.get(categoryName).put(dateString, occurrence);
                                }
                            } else { // no category inside
                                Map<String, Integer> newCategory = new HashMap<>();
                                newCategory.put(dateString, occurrence);
                                categoryDates.put(categoryName, newCategory);
                            }
                        }

                        // Get every name with occurrence (with no bad word from name list)
                        for (Pair<String, Integer> names : mapsList.get("doc_name")) {
                            String name = names.getKey();
                            Integer occurrence = names.getValue();
                            ArrayList<String> tmpList = new ArrayList<String>();
                            tmpList.add(name);
                            if(Collections.disjoint(tmpList,stopWords)) {
                                if (categoryNames.containsKey(categoryName)) { // has category inside
                                    // If time in map plus one, else add new entry
                                    if (categoryNames.get(categoryName).containsKey(name)) { // has time inside
                                        int counter = categoryNames.get(categoryName).get(name);
                                        categoryNames.get(categoryName).put(name, ++counter);
                                    } else { // no time inside
                                        categoryNames.get(categoryName).put(name, occurrence);
                                    }
                                } else { // no category inside
                                    Map<String, Integer> newCategory = new HashMap<>();
                                    newCategory.put(name, occurrence);
                                    categoryNames.put(categoryName, newCategory);
                                }

                                // for global occurence
                                if (globalName.containsKey(name)){
                                    int counter = globalName.get(name);
                                    globalName.put(name, ++counter);
                                } else {
                                    globalName.put(name, occurrence);
                                }
                            }
                        }
                    }
                }
            }
        }
        printWriter.write(stringBuilder.toString());
        printWriter.close();
        System.out.println("\n>>> Save to " + filename+"\n");

        mapExportCSV(categoryLocations,"cat_loc","Location");
        mapExportCSV(categoryDates,"cat_time","Year");
        mapExportCSV(categoryNames,"cat_name","Name");
        singleMapExportCSV(globalName,"global_name","Name");
    }

    private void singleMapExportCSV(Map<String,Integer> map, String namePrefix,String fieldName) throws FileNotFoundException {
        String filename = "results/"+namePrefix+"/"+namePrefix+".csv";
        File file = new File(filename);
        file.getParentFile().mkdirs();
        PrintWriter printWriter = new PrintWriter(file);
        StringBuilder stringBuilder = new StringBuilder();
        // Header in csv file
        stringBuilder.append(fieldName);
        stringBuilder.append(',');
        stringBuilder.append("Occur");

        int counter = 1;
        int maxCounter = 10; // firs 10 occurence (max)
        Map<String,Integer> sortedMap = getSortedMap(map);
        for (Map.Entry<String, Integer> entry: sortedMap.entrySet()) {
            if(counter > maxCounter){
                break;
            }
            if(entry.getValue() > 1) { //if something is only 1 time it is too low for us
                String key = entry.getKey().replaceAll(",", "");
                stringBuilder.append("\n");
                stringBuilder.append(String.format("{%s}", key));
                stringBuilder.append(",");
                stringBuilder.append(entry.getValue().toString());
                counter++;
            }
        }
        printWriter.write(stringBuilder.toString());
        printWriter.close();
        System.out.println(">>> Save to " + filename);
    }

    private void mapExportCSV(Map<String,Map<String,Integer>> map,String name_prefix,String field_name) throws FileNotFoundException {
        for (Map.Entry<String,Map<String,Integer>> mapEntry: map.entrySet()) {
            // One file for every groups
            String key = mapEntry.getKey();
            Map<String,Integer> value = mapEntry.getValue();

            String filename = "results/"+name_prefix+"/"+name_prefix+"_"+key+".csv";
            File file = new File(filename);
            file.getParentFile().mkdirs();
            PrintWriter printWriter = new PrintWriter(file);
            StringBuilder stringBuilder = new StringBuilder();

            // Header in csv file
            stringBuilder.append(field_name);
            stringBuilder.append(',');
            stringBuilder.append("Occur");

            int counter = 1;
            int maxCounter = 10; // firs 10 occurence (max)
            Map<String,Integer> sortedMap = getSortedMap(value);
            for (Map.Entry<String, Integer> entry: sortedMap.entrySet()) {
                if(counter > maxCounter){
                    break;
                }
                if(entry.getValue() > 1) { //if something is only 1 time it is too low for us
                    String tmp_key = entry.getKey().replaceAll(",", "");
                    stringBuilder.append("\n");
                    stringBuilder.append(String.format("{%s}", tmp_key));
                    stringBuilder.append(",");
                    stringBuilder.append(entry.getValue().toString());
                    counter++;
                }
            }
            printWriter.write(stringBuilder.toString());
            printWriter.close();
            System.out.println(">>> Save to " + filename);
        }
    }

    private Map<String, Integer> getSortedMap(Map<String, Integer> map) {
        // Create array and sort by value
        Object[] objects = map.entrySet().toArray();
        Arrays.sort(objects, (Comparator) (o1, o2) -> ((Map.Entry<String, Integer>) o2).getValue()
                .compareTo(((Map.Entry<String, Integer>) o1).getValue()));
        // Create array of locations in proper order
        // If counter is the same position is like in original text
        Map<String, Integer> sortedMap = new LinkedHashMap<>();
        for (Object object : objects) {
            sortedMap.put(((Map.Entry<String, Integer>) object).getKey(), ((Map.Entry<String, Integer>) object).getValue());
        }
        return sortedMap;
    }

}
