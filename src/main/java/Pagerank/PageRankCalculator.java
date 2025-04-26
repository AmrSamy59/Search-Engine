package Pagerank;

import com.mongodb.client.*;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import org.bson.Document;
import org.bson.types.ObjectId;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.*;
import java.util.concurrent.*;

public class PageRankCalculator {
    private MongoDatabase database;
    private MongoCollection<Document> docsCollection;
    private final Map<String, Double> pageRanks = new ConcurrentHashMap<>();
    private final Map<String, List<String>> incomingLinks;
    private final Map<String, Integer> outDegreeCache;

    private final double dampingFactor = 0.85;
    private final int iterations = 30;

    public PageRankCalculator(Map<String, List<String>> incomingLinks, Map<String, Integer> outDegreeCache) {
        try {
            Dotenv dotenv = Dotenv.load();
            String connectionString = dotenv.get("MONGO_URL");
            String dbName = dotenv.get("MONGO_DB_NAME");

            MongoClient client = MongoClients.create(connectionString);
            database = client.getDatabase(dbName);
            docsCollection = database.getCollection("docs");

            this.incomingLinks = incomingLinks;
            this.outDegreeCache = outDegreeCache;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Initialize PageRank values to 1.0 for each page
    public void initializePageRanks() {
        for (Document doc : docsCollection.find()) {
            String id = doc.getObjectId("_id").toHexString();
            pageRanks.put(id, 1.0);
        }
        System.out.println("[PageRankCalculator] Initialized " + pageRanks.size() + " pages.");
    }

    public void calculatePageRanks() {
        System.out.println("[PageRankCalculator] Calculating PageRanks...");
        long numPages = docsCollection.countDocuments();
        ExecutorService executorService = Executors.newFixedThreadPool(8);

        for (int it = 0; it < iterations; it++) {
            Map<String, Double> newPageRanks = new ConcurrentHashMap<>();
            List<Callable<Void>> tasks = new ArrayList<>();

            for (String pageId : pageRanks.keySet()) {
                tasks.add(() -> {
                    double newRank = (1.0 - dampingFactor) / numPages;
                    List<String> parents = incomingLinks.getOrDefault(pageId, Collections.emptyList());

                    for (String parentId : parents) {
                        double parentRank = pageRanks.getOrDefault(parentId, 1.0);
                        int outDegree = outDegreeCache.getOrDefault(parentId, 1);
                        newRank += dampingFactor * (parentRank / outDegree);
                    }
                    newPageRanks.put(pageId, newRank);
                    return null;
                });
            }

            try {
                executorService.invokeAll(tasks);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            pageRanks.clear();
            pageRanks.putAll(newPageRanks);

            System.out.println("[PageRankCalculator] Completed iteration " + (it + 1));
        }

        executorService.shutdown();
    }

    public void savePageRanks() {
        System.out.println("[PageRankCalculator] Saving PageRanks to database...");
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

        System.out.println("[PageRankCalculator] PageRanks saved successfully.");
    }
}
