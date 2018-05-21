import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;

public class Luc {
    public static void main(String[] args) throws IOException, ParseException {

        // Read index from nutch
        Directory index = FSDirectory.open(Paths.get("./data/index"));
        IndexReader reader = DirectoryReader.open(index);

        // Check how many docs are in index
        int index_size = reader.numDocs();
        System.out.println("In index are "+index_size+" docs. (In solr are 524, should be the same!)\n");


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
}
