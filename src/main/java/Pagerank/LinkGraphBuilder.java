package Pagerank;

import com.mongodb.client.*;
import org.bson.Document;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.*;
import java.util.concurrent.*;

public class LinkGraphBuilder {
    // Using Concurrent HashMap to ensure thread-safety
    private final Map<String, String> urlToId = new ConcurrentHashMap<>();
//    private final Map<String, String> idToUrl = new ConcurrentHashMap<>();

    private MongoDatabase database;
    private MongoCollection<Document> docsCollection;

    private final Map<String, List<String>> incomingLinks = new ConcurrentHashMap<>();
    private final Map<String, Integer> outDegreeCache = new ConcurrentHashMap<>();
    List<Document> documents;

    private ExecutorService executor;

    public LinkGraphBuilder() {
        try {
            Dotenv dotenv = Dotenv.load();
            String connectionString = dotenv.get("MONGO_URL");
            String dbName = dotenv.get("MONGO_DB_NAME");

            MongoClient client = MongoClients.create(connectionString);
            database = client.getDatabase(dbName);
            docsCollection = database.getCollection("documents");

            executor = Executors.newFixedThreadPool(8);
            // Use projection to fetch only _id and url fields
            Document projection = new Document("_id", 1).append("url", 1).append("links", 1);
            // Fetch all documents at once into a List
            documents = docsCollection.find().projection(projection).into(new ArrayList<>());


            System.out.println("[LinkGraphBuilder] Connected to MongoDB successfully.");
        } catch (Exception e) {
            System.err.println("[LinkGraphBuilder] Failed to connect to MongoDB: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public List<Document> getDocuments() {
        return documents;
    }


    public void buildUrlIdMaps() {
        System.out.println("[LinkGraphBuilder] Building URL to ID maps...");

        List<Future<?>> futures = new ArrayList<>();
        for (Document doc : documents) {
            futures.add(executor.submit(() -> {
                String URL = doc.getString("url");
                if (URL != null) {
                    String ID = doc.getObjectId("_id").toHexString();
                    urlToId.put(URL, ID);
                    //idToUrl.put(ID, URL);
                    System.out.println("Processed URL: " + URL);
                }
            }));
        }

        waitForFutures(futures);
        System.out.println("[LinkGraphBuilder] URL to ID maps built. Total: " + urlToId.size());
    }

    public void buildLinkGraphInMemory() {
        System.out.println("[LinkGraphBuilder] Building in-memory link graph...");
        List<Future<?>> futures = new ArrayList<>();

        for (Document doc : documents) {
            futures.add(executor.submit(() -> {
                String parentURL = doc.getString("url");
                if (parentURL != null) {
                    String parentID = urlToId.get(parentURL);

                    List<String> childLinks = doc.getList("links", String.class);
                    if (parentID != null && childLinks != null) {
                        outDegreeCache.put(parentID, childLinks.size());
                        for (String childURL : childLinks) {
                            String childID = urlToId.get(childURL);
                            if (childID == null) continue; // Skip missing pages
                            incomingLinks.computeIfAbsent(childID, k -> new ArrayList<>()).add(parentID);
                        }
                    }
                }
            }));
        }

        waitForFutures(futures);
        System.out.println("[LinkGraphBuilder] In-memory link graph built. Total links: " + incomingLinks.size());
    }

    private void waitForFutures(List<Future<?>> futures) {
        try {
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void shutdownExecutor() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    public Map<String, List<String>> getIncomingLinks() {
        return incomingLinks;
    }

    public Map<String, Integer> getOutDegreeCache() {
        return outDegreeCache;
    }
}