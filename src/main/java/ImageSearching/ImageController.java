package ImageSearching;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
public class ImageController {
    @Autowired
    private ImageFeatureExtractor featureExtractor;
    @Autowired
    private ImageSearchService imageSearchService;

    @PostMapping("/extract-features")
    public float[] extractFeatures(@RequestParam("file") MultipartFile file) throws IOException {
        return imageSearchService.extractFeatures(file);
    }

    @PostMapping("/upload")
    public void uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        imageSearchService.saveImage(file);
    }

    @PostMapping("/upload-from-url")
    public void uploadImageFromUrl(@RequestParam("url") String url) throws IOException, InterruptedException {
        imageSearchService.processImageFromUrl(url);
    }

    @PostMapping("/search")
    public List<Image> searchSimilarImages(@RequestParam("file") MultipartFile file) {
        return imageSearchService.searchSimilarImages(file);
    }
}