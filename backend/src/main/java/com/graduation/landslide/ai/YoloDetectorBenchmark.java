package com.graduation.landslide.ai;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/** Java + OpenCV DNN benchmark (same path as YoloDetector.detect). */
public final class YoloDetectorBenchmark {

    private static final int WARMUP = 5;
    private static final int RUNS = 30;

    public static void main(String[] args) throws Exception {
        String modelPath = args.length > 0 ? args[0] : "models/landslide-yolov8.onnx";
        String imagePath = args.length > 1 ? args[1] : resolveDefaultImage();
        if (imagePath == null) {
            System.err.println("FAIL: no test image");
            System.exit(1);
        }
        Path modelFile = Paths.get(modelPath);
        if (!Files.isRegularFile(modelFile)) {
            System.err.println("FAIL: model not found: " + modelFile.toAbsolutePath());
            System.exit(1);
        }
        byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));
        YoloDetector detector = createDetector(modelPath);
        if (!detector.isModelReady()) {
            System.err.println("FAIL: model not ready");
            System.exit(1);
        }
        System.out.println("=== YOLO Java+OpenCV DNN benchmark ===");
        System.out.println("Model : " + modelFile.toAbsolutePath());
        System.out.println("Image : " + Paths.get(imagePath).toAbsolutePath());
        System.out.println("Warmup: " + WARMUP + " | Runs: " + RUNS);
        for (int i = 0; i < WARMUP; i++) detector.detect(imageBytes);
        long[] ms = new long[RUNS];
        int lastBoxes = 0;
        for (int i = 0; i < RUNS; i++) {
            long t0 = System.nanoTime();
            lastBoxes = detector.detect(imageBytes).size();
            ms[i] = (System.nanoTime() - t0) / 1_000_000L;
        }
        Arrays.sort(ms);
        long median = ms[RUNS / 2];
        System.out.printf(Locale.ROOT, "median : %d ms  <-- resume%n", median);
        System.out.printf(Locale.ROOT, "min/avg/p95/max : %d / %.1f / %d / %d ms%n",
                ms[0], Arrays.stream(ms).average().orElse(0), ms[(int)Math.ceil(RUNS*0.95)-1], ms[RUNS-1]);
        System.out.println("boxes (last): " + lastBoxes);
    }

    private static YoloDetector createDetector(String modelPath) throws Exception {
        YoloDetector detector = new YoloDetector();
        setField(detector, "modelPath", modelPath);
        setField(detector, "inputWidth", 640);
        setField(detector, "inputHeight", 640);
        setField(detector, "confThreshold", 0.45f);
        setField(detector, "nmsThreshold", 0.45f);
        setField(detector, "classNames", new String[]{"landslide"});
        detector.init();
        return detector;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = YoloDetector.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static String resolveDefaultImage() throws IOException {
        Path valDir = Paths.get("E:/landslide-yolo/images/val");
        if (!Files.isDirectory(valDir)) return null;
        try (Stream<Path> s = Files.list(valDir)) {
            return s.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".png") || n.endsWith(".jpg");
            }).min(Comparator.comparing(Path::toString)).map(Path::toString).orElse(null);
        }
    }
}