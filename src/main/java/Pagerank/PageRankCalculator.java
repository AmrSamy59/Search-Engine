package Pagerank;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.client.*;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import org.bson.Document;
import org.bson.types.ObjectId;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.*;
import java.util.concurrent.*;

public class PageRankCalculator {
    private final MongoClient client;
    private final MongoDatabase database;
    private final MongoCollection<Document> docsCollection;
    private final Map<String, Double> pageRanks = new ConcurrentHashMap<>();
    private final Map<String, List<String>> incomingLinks;
    private final Map<String, Integer> outDegreeCache;
    private final List<Document> documents;
    private final double dampingFactor = 0.85;
    private final int iterations = 50;

    public PageRankCalculator(Map<String, List<String>> incomingLinks, Map<String, Integer> outDegreeCache, List<Document> documents) {
        try {
            Dotenv dotenv = Dotenv.load();
            String connectionString = dotenv.get("MONGO_URL");
            String dbName = dotenv.get("MONGO_DB_NAME");

            client = MongoClients.create(connectionString);
            database = client.getDatabase(dbName);
            docsCollection = database.getCollection("documents");
            this.documents = documents;
            this.incomingLinks = incomingLinks;
            this.outDegreeCache = outDegreeCache;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void initializePageRanks() {
        Set<String> incomingLinkIds = incomingLinks.keySet();
        for (Document doc : documents) {
            Object id = doc.get("_id");
            if (id instanceof ObjectId) {
                String idStr = ((ObjectId) id).toHexString();
                pageRanks.put(idStr, 1.0);
                // Ensure all pages in incomingLinks have an entry
                incomingLinks.putIfAbsent(idStr, Collections.emptyList());
            } else {
                System.out.println("[PageRankCalculator] Skipping document with invalid _id: " + id);
            }
        }
        // Initialize any additional pages from incomingLinks not in documents
        for (String idStr : incomingLinkIds) {
            pageRanks.putIfAbsent(idStr, 1.0);
        }
        System.out.println("[PageRankCalculator] Initialized " + pageRanks.size() + " pages.");
    }

    public void calculatePageRanks() {
        System.out.println("[PageRankCalculator] Calculating PageRanks...");
        long numPages = documents.size();
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
                        int outDegree = outDegreeCache.getOrDefault(parentId, 0);
                        if (outDegree > 0) {
                            newRank += dampingFactor * (parentRank / outDegree);
                        }
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

        // Log PageRank distribution
        double minRank = pageRanks.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maxRank = pageRanks.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double avgRank = pageRanks.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        System.out.println("[PageRankCalculator] PageRank range: min = " + minRank + ", max = " + maxRank + ", avg = " + avgRank);

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void savePageRanks() {
        System.out.println("[PageRankCalculator] Saving PageRanks to database...");
        double totalSum = pageRanks.values().stream().mapToDouble(Double::doubleValue).sum();
        pageRanks.replaceAll((id, rank) -> rank / totalSum);

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
            try {
                docsCollection.bulkWrite(bulkUpdates);
            } catch (MongoBulkWriteException e) {
                System.err.println("[PageRankCalculator] Error during bulk write: " + e.getMessage());
            }
        }

        System.out.println("[PageRankCalculator] PageRanks saved successfully.");
    }

    public void close() {
        if (client != null) {
            client.close();
        }
    }
}