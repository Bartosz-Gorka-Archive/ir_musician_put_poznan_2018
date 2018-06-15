import javafx.util.Pair;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {

    public static final String DB_FILE_NAME = "results/doc_db.ser";

    public void saveDocumentsDb(Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> documentDbCatMap) {
        try {
            new File(DB_FILE_NAME).getParentFile().mkdirs();
            FileOutputStream fileOutputStream = new FileOutputStream(DB_FILE_NAME);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(documentDbCatMap);
            objectOutputStream.flush();
            objectOutputStream.close();
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("\n>>> Saved DB\n");
    }

    public Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> loadDocumentsDb() {
        Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>> documentDbCatMap = new Pair<>(null, null);
        try {
            ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(DB_FILE_NAME));
            documentDbCatMap = (Pair<List<LinkedHashMap<String, List<Pair<String, Integer>>>>, Map<String, List<String>>>) objectInputStream.readObject();
            objectInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.out.println("Class not found");
            e.printStackTrace();
        }
        System.out.println("\n>>> Loaded DB\n");
        return documentDbCatMap;
    }
}
