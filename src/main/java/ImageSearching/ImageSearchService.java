package ImageSearching;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

@Service
public class ImageSearchService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private ImageFeatureExtractor featureExtractor;

    @PostConstruct
    public void createIndexes() {
        // Create indexes for efficient search
        mongoTemplate.indexOps("images").ensureIndex(
                new Index().on("features", Sort.Direction.ASC)
        );
    }

    public void saveImage(MultipartFile file) throws IOException {
        float[] features = featureExtractor.extractFeatures(file.getInputStream().readAllBytes());
        Image image = new Image();
        image.setFeatures(features);
        imageRepository.save(image);
    }

    public void processImageFromUrl(String url) throws IOException, InterruptedException {
        System.out.println("Processing image from " + url);

        // Validate URL
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Image URL cannot be empty");
        }

        // Fetch image
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] imageData = response.body();

        // Verify it's an image
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(imageData)) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new IOException("URL does not point to a valid image: " + url);
            }
        }

        // Extract features
        float[] features = featureExtractor.extractFeatures(imageData);

        // Create and save Image object
        Image image = new Image();
        // TODO: save unique image ID (hash)
        image.setId(UUID.randomUUID().toString());
        image.setFeatures(features);
        image.setUrl(url);
        mongoTemplate.save(image, "images");
        System.out.println("Saved image with ID: " + image.getId() + " and url: " + url);
    }

    public float[] extractFeatures(MultipartFile file) {
        try {
            return featureExtractor.extractFeatures(file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract features", e);
        }
    }

    public List<Image> searchSimilarImages(MultipartFile file) {
        try {
            // Extract features of the input image
            float[] queryFeatures = featureExtractor.extractFeatures(file.getBytes());

            // Simple search: Fetch all images and compute Euclidean distance
            List<Image> allImages = mongoTemplate.findAll(Image.class, "images");
            allImages.sort((a, b) -> {
                float distA = calculateEuclideanDistance(queryFeatures, a.getFeatures());
                float distB = calculateEuclideanDistance(queryFeatures, b.getFeatures());
                return Float.compare(distA, distB);
            });

            // Return top 10 similar images
            return allImages.subList(0, Math.min(10, allImages.size()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to search images", e);
        }
    }

    private float calculateEuclideanDistance(float[] v1, float[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        float sum = 0;
        for (int i = 0; i < v1.length; i++) {
            float diff = v1[i] - v2[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }
}