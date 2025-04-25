package ImageSearching;

import ai.onnxruntime.*;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class ImageFeatureExtractor {

    private OrtEnvironment env;
    private OrtSession session;

    private static final int HEIGHT = 224;
    private static final int WIDTH = 224;

    @PostConstruct
    public void init() throws OrtException, IOException {
        env = OrtEnvironment.getEnvironment();
        session = env.createSession("src/main/resources/models/model.onnx",
                new OrtSession.SessionOptions());
    }

    public float[] extractFeatures(byte[] imageBytes) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null) throw new IOException("Invalid image");

        float[][][][] input = preprocess(image); // [1, 3, 224, 224]
        OnnxTensor inputTensor = OnnxTensor.createTensor(env, input);

        OrtSession.Result result = session.run(Collections.singletonMap("pixel_values", inputTensor));
        float[][] pooledOutput = (float[][]) result.get(0).getValue(); // [1][512]

        return normalizeVector(pooledOutput[0]);
    }

    private float[][][][] preprocess(BufferedImage original) {
        BufferedImage resized = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.drawImage(original, 0, 0, WIDTH, HEIGHT, null);
        g.dispose();

        float[][][][] input = new float[1][3][HEIGHT][WIDTH]; // CHW

        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                Color color = new Color(resized.getRGB(x, y));

                // Normalize using CLIP's mean/std
                input[0][0][y][x] = ((color.getRed() / 255.0f) - 0.485f) / 0.229f;
                input[0][1][y][x] = ((color.getGreen() / 255.0f) - 0.456f) / 0.224f;
                input[0][2][y][x] = ((color.getBlue() / 255.0f) - 0.406f) / 0.225f;
            }
        }

        return input;
    }

    private float[] normalizeVector(float[] vector) {
        float sumSq = 0f;
        for (float v : vector) sumSq += v * v;
        float norm = (float) Math.sqrt(sumSq);
        if (norm == 0) return vector;

        float[] out = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            out[i] = vector[i] / norm;
        }
        return out;
    }

}
