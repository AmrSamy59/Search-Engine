package ImageSearching;

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.transferlearning.TransferLearning;
import org.deeplearning4j.util.ModelSerializer;
import org.deeplearning4j.zoo.PretrainedType;
import org.deeplearning4j.zoo.ZooModel;
import org.deeplearning4j.zoo.model.ResNet50;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.ImagePreProcessingScaler;
import org.nd4j.linalg.factory.Nd4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Service
public class ImageFeatureExtractor {

    private ComputationGraph model;
    private static final int HEIGHT = 224;
    private static final int WIDTH = 224;
    private static final int CHANNELS = 3;
    private static final String FEATURE_LAYER = "flatten_1";  // ResNet50's feature extraction layer

    @PostConstruct
    public void init() throws IOException {
        // Load the model from file or download if not exists
        File modelFile = new File("src/main/resources/models/resnet50.zip");
        if (!modelFile.exists()) {
            downloadAndSaveModel();
        }
        model = ModelSerializer.restoreComputationGraph(modelFile);

        // Create a feature extraction model that outputs the flatten layer
        model = new TransferLearning.GraphBuilder(model)
                .setFeatureExtractor(FEATURE_LAYER)
                .build();
    }

    public float[] extractFeatures(byte[] imageStream) throws IOException {
        // Load and preprocess the image
        InputStream inputStream = new ByteArrayInputStream(imageStream);
        // Load and preprocess the image
        BufferedImage image = ImageIO.read(inputStream);
        if (image == null) {
            throw new IOException("Could not read image");
        }

        // Convert image to INDArray
        INDArray imageArray = preprocessImage(image);

        // Extract features
        INDArray featuresArray = model.feedForward(imageArray, false).get(FEATURE_LAYER);

        // Convert to float array
        return featuresArray.data().asFloat();
    }

    private INDArray preprocessImage(BufferedImage image) {
        // Resize image to match network input
        BufferedImage resizedImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        resizedImage.getGraphics().drawImage(image, 0, 0, WIDTH, HEIGHT, null);

        // Convert to tensor format
        int[] pixels = new int[WIDTH * HEIGHT];
        resizedImage.getRGB(0, 0, WIDTH, HEIGHT, pixels, 0, WIDTH);

        // Create an empty INDArray with the right shape [1, channels, height, width]
        INDArray imageArray = Nd4j.create(1, CHANNELS, HEIGHT, WIDTH);

        // Fill in RGB values
        for (int i = 0; i < HEIGHT; i++) {
            for (int j = 0; j < WIDTH; j++) {
                int pixel = pixels[i * WIDTH + j];

                // Red, Green, Blue channels (RGB order)
                imageArray.putScalar(new int[]{0, 0, i, j}, ((pixel >> 16) & 0xFF));
                imageArray.putScalar(new int[]{0, 1, i, j}, ((pixel >> 8) & 0xFF));
                imageArray.putScalar(new int[]{0, 2, i, j}, (pixel & 0xFF));
            }
        }

        // Apply ResNet50-specific preprocessing
        // Scale pixel values to [0,1]
        DataNormalization scaler = new ImagePreProcessingScaler(0, 1);
        scaler.transform(imageArray);

        return imageArray;
    }

    private void downloadAndSaveModel() throws IOException {
        System.out.println("Downloading ResNet50 model. This may take a while...");

        // Download ResNet50 pre-trained on ImageNet
        ZooModel zooModel = ResNet50.builder().build();
        ComputationGraph fullModel = (ComputationGraph) zooModel.initPretrained(PretrainedType.IMAGENET);

        // Save the model to file
        String modelPath = "src/main/resources/models/resnet50.zip";
        File modelFile = new File(modelPath);
        modelFile.getParentFile().mkdirs();  // Create directories if they don't exist
        ModelSerializer.writeModel(fullModel, modelFile, true);

        System.out.println("ResNet50 model downloaded and saved successfully.");
    }
}