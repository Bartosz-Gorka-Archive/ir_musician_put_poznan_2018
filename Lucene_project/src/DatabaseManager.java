import javafx.util.Pair;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {

    public static final String DB_FILE_NAME = "results/doc_db.ser";

    public void saveDocDB(Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> document_db_cat_map) {
        try {
            File f = new File(DB_FILE_NAME);
            f.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(DB_FILE_NAME);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(document_db_cat_map);
            oos.close();
            fos.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        System.out.println("\n>>> Saved DB\n");
    }

    public Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> loadDocDB() {
        Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> document_db_cat_map = new Pair<>(null, null);
        try {
            FileInputStream fis = new FileInputStream(DB_FILE_NAME);
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
}
