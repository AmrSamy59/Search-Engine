package Pagerank;

import com.mongodb.client.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.*;
import java.util.concurrent.*;

public class LinkGraphBuilder {
    // Using Concurrent HashMap to ensure thread-safety
    Map<String, String> urlToId = new ConcurrentHashMap<>();
    Map<String, String> idToUrl = new ConcurrentHashMap<>();

    private MongoDatabase database;
    private MongoCollection<Document> docsCollection;
    private MongoCollection<Document> linksCollection;


    private ExecutorService executor;

    public LinkGraphBuilder() {
        try {
            Dotenv dotenv = Dotenv.load();
            String connectionString = dotenv.get("MONGO_URL");
            String dbName = dotenv.get("MONGO_DB_NAME");

            MongoClient client = MongoClients.create(connectionString);
            database = client.getDatabase(dbName);
            docsCollection = database.getCollection("documents");
            linksCollection = database.getCollection("links");


            executor = Executors.newFixedThreadPool(8);

            System.out.println("[LinkGraphBuilder] Connected to MongoDB successfully.");
        } catch (Exception e) {
            System.err.println("[LinkGraphBuilder] Failed to connect to MongoDB: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }


    public void buildUrlIdMaps() {
        System.out.println("[LinkGraphBuilder] Building URL to ID maps in parallel...");

        List<Future<?>> futures = new ArrayList<>();
        MongoCursor<Document> cursor = docsCollection.find().batchSize(500).iterator();

        while (cursor.hasNext()) {
            Document doc = cursor.next();
            futures.add(executor.submit(() -> {
                String URL = doc.getString("url");
                if (URL != null) {
                    String ID = doc.getObjectId("_id").toHexString();
                    urlToId.put(URL, ID);
                    idToUrl.put(ID, URL);
                    System.out.println("Processed URL: " + URL); // Log each URL processed using System.out
                }
            }));
        }

        // Wait for all tasks to finish
        try {
            for (Future<?> future : futures) {
                future.get(); // Block until all tasks complete
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        System.out.println("[LinkGraphBuilder] URL to ID maps built. Total entries: " + urlToId.size());
    }

    // Build the links collection concurrently
    public void buildLinksCollection() {
        System.out.println("[LinkGraphBuilder] Building links collection in parallel...");

        linksCollection.drop();

        List<Future<?>> futures = new ArrayList<>();

        MongoCursor<Document> cursor = docsCollection.find().batchSize(500).iterator();

        while (cursor.hasNext()) {
            Document doc = cursor.next();

            futures.add(executor.submit(() -> {
                String ParentURL = doc.getString("url");
                String ParentID = urlToId.get(ParentURL);

                // Log the parent URL processed
                System.out.println("Processing Parent URL: " + ParentURL);

                List<String> ChildLinks = doc.getList("links", String.class);
                if (ParentID != null && ChildLinks != null) {
                    List<Document> linksDocs = new ArrayList<>();
                    for (String childLink : ChildLinks) {
                        if (childLink != null) {
                            String ChildID = urlToId.get(childLink);
                            if (ChildID == null) continue; // Ignore links we didn't crawl

                            Document newDoc = new Document();
                            newDoc.put("from_id", ParentID);
                            newDoc.put("to_id", ChildID);
                            linksDocs.add(newDoc);
                        }
                    }

                    if (!linksDocs.isEmpty()) {
                        synchronized (linksCollection) {
                            linksCollection.insertMany(linksDocs);
                        }
                    }
                }
            }));
        }

        // Wait for all tasks to finish
        try {
            for (Future<?> future : futures) {
                future.get(); // Block until all tasks complete
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        System.out.println("[LinkGraphBuilder] Link collection built successfully.");
    }

    // Shutdown executor after all work is done
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

    // Main method to start the process
    public static void main(String[] args) {
        LinkGraphBuilder builder = new LinkGraphBuilder();
        builder.buildUrlIdMaps();
        builder.buildLinksCollection();
        builder.shutdownExecutor();
    }
}
