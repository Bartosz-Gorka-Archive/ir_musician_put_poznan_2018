import javafx.util.Pair;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.util.*;

public class Exporter {

    public void exportToCSV(List<LinkedHashMap<String, List<Pair<String, Integer>>>> document_db, Map<String, List<String>> category_map) throws FileNotFoundException {
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

    private void singleMapExportCSV(Map<String,Integer> map, String name_prefix,String field_name) throws FileNotFoundException {
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

    private void mapExportCSV(Map<String,Map<String,Integer>> map,String name_prefix,String field_name) throws FileNotFoundException {

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

    private Map<String, Integer> getSortedMap(Map<String, Integer> map) {
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

}
