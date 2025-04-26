package Pagerank;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import io.github.cdimascio.dotenv.Dotenv;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PageRankCalculator {
    private MongoDatabase database;
    private MongoCollection<Document> docsCollection;
    private MongoCollection<Document> linksCollection;
    Map<String, Double> pageRanks = new HashMap<>();
    Map<String, List<String>> incomingLinks = new HashMap<>();
    Map<String, Integer> outDegreeCache = new HashMap<>();
    double dampingFactor = 0.85;
    int iterations = 30;

    public PageRankCalculator() {
        try {
            Dotenv dotenv = Dotenv.load();
            String connectionString = dotenv.get("MONGO_URL");
            String dbName = dotenv.get("MONGO_DB_NAME");
            MongoClient client = MongoClients.create(connectionString);
            database = client.getDatabase(dbName);

            docsCollection = database.getCollection("docs");
            linksCollection = database.getCollection("links");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Initialize PageRank values to 1.0 for each page
    public void intializePageRanks() {
        for (Document doc : docsCollection.find()) {
            String id = doc.getObjectId("_id").toHexString();
            pageRanks.put(id, 1.0);
        }
    }

    // Load links from the links collection into the incomingLinks map
    public void loadLinks() {
        for (Document doc : linksCollection.find()) {
            String fromId = doc.getString("from_id");
            String toId = doc.getString("to_id");
            incomingLinks.computeIfAbsent(toId, k -> new ArrayList<>()).add(fromId);

            // Cache out-degree
            outDegreeCache.put(fromId, outDegreeCache.getOrDefault(fromId, 0) + 1);
        }
    }

    // Get the out-degree from the cached value
    private int getOutDegree(String pageId) {
        return outDegreeCache.getOrDefault(pageId, 0);
    }

    // Calculate PageRank using the given damping factor and iterations
    public void calculatePageRanks() {
        long numPages = docsCollection.countDocuments();
        ExecutorService executorService = Executors.newFixedThreadPool(8);

        for (int i = 0; i < iterations; i++) {
            Map<String, Double> newPageRanks = new HashMap<>();
            List<Callable<Void>> tasks = new ArrayList<>();

            for (Document doc : docsCollection.find()) {
                String pageId = doc.getObjectId("_id").toHexString();
                tasks.add(() -> {
                    double newPageRank = (1 - dampingFactor) / numPages;
                    List<String> parents = incomingLinks.getOrDefault(pageId, new ArrayList<>());

                    for (String parentId : parents) {
                        double parentPageRank = pageRanks.getOrDefault(parentId, 1.0);
                        int outDegree = getOutDegree(parentId);
                        newPageRank += dampingFactor * (parentPageRank / outDegree);
                    }
                    newPageRanks.put(pageId, newPageRank);
                    return null;
                });
            }

            try {
                executorService.invokeAll(tasks);  // Execute all tasks concurrently
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // After parallel computation, update pageRanks
            pageRanks = new HashMap<>(newPageRanks);
        }

        executorService.shutdown();  // Shutdown the executor service
    }


    // Save PageRank values back to the database using bulk operations
    public void savePageRanks() {
        List<WriteModel<Document>> bulkUpdates = new ArrayList<>();
        for (Map.Entry<String, Double> entry : pageRanks.entrySet()) {
            String pageId = entry.getKey();
            Double pageRank = entry.getValue();
            bulkUpdates.add(new UpdateOneModel<>(
                    new Document("_id", new ObjectId(pageId)),
                    new Document("$set", new Document("pageRank", pageRank))
            ));
        }

        if (!bulkUpdates.isEmpty()) {
            docsCollection.bulkWrite(bulkUpdates);
        }
    }
}
