package com.graduation.landslide.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfRect2d;
import org.opencv.core.Rect2d;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class YoloDetector {

    @Value("${ai.yolo.model-path}")
    private String modelPath;

    @Value("${ai.yolo.input-width:640}")
    private int inputWidth;

    @Value("${ai.yolo.input-height:640}")
    private int inputHeight;

    @Value("${ai.yolo.conf-threshold:0.002}")
    private float confThreshold;

    @Value("${ai.yolo.nms-threshold:0.45}")
    private float nmsThreshold;

    @Value("${ai.yolo.class-names:landslide}")
    private String[] classNames;

    private Net net;
    private volatile boolean modelReady = false;
    private volatile Map<String, Object> lastDebugInfo = Collections.emptyMap();

    @PostConstruct
    public void init() {
        try {
            OpenCV.loadLocally();
            File modelFile = new File(modelPath);
            if (!modelFile.exists()) {
                log.warn("YOLO model file not found: {}. Detector will run in disabled mode.", modelFile.getAbsolutePath());
                return;
            }

            this.net = Dnn.readNetFromONNX(modelPath);
            if (this.net == null || this.net.empty()) {
                log.warn("YOLO model load failed: {}. Detector will run in disabled mode.", modelPath);
                return;
            }

            this.modelReady = true;
            log.info("YOLOv8 model loaded. path={}, input={}x{}, conf={}, nms={}",
                    modelPath, inputWidth, inputHeight, confThreshold, nmsThreshold);
        } catch (Exception ex) {
            log.warn("YOLO init failed, detector disabled. reason={}", ex.getMessage());
        }
    }

    public synchronized List<DetectionBox> detect(byte[] imageBytes) {
        if (!modelReady || imageBytes == null || imageBytes.length == 0) {
            Map<String, Object> debug = new HashMap<>();
            debug.put("modelReady", modelReady);
            debug.put("imageBytesLength", imageBytes == null ? 0 : imageBytes.length);
            debug.put("message", "skip detect because model not ready or image empty");
            lastDebugInfo = debug;
            return new ArrayList<>();
        }

        Mat image = null;
        Mat blob = null;
        Mat rawOutput = null;
        Mat output = null;
        Map<String, Object> debug = new HashMap<>();
        try {
            image = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_COLOR);
            if (image == null || image.empty()) {
                throw new IllegalArgumentException("Image bytes cannot be decoded");
            }

            blob = Dnn.blobFromImage(
                    image,
                    1.0 / 255.0,
                    new Size(inputWidth, inputHeight),
                    new Scalar(0, 0, 0),
                    true,
                    false
            );
            net.setInput(blob);
            rawOutput = net.forward();
            output = normalizeOutput(rawOutput);

            debug.put("rawDims", rawOutput.dims());
            debug.put("outputRows", output.rows());
            debug.put("outputCols", output.cols());
            debug.put("confThreshold", confThreshold);
            debug.put("nmsThreshold", nmsThreshold);

            double xScale = image.cols() / (double) inputWidth;
            double yScale = image.rows() / (double) inputHeight;

            List<Rect2d> boxes = new ArrayList<>();
            List<Float> scores = new ArrayList<>();
            List<Integer> classIds = new ArrayList<>();
            float maxRawScore = Float.NEGATIVE_INFINITY;

            for (int i = 0; i < output.rows(); i++) {
                float[] row = new float[output.cols()];
                output.get(i, 0, row);
                if (row.length > 4 && row[4] > maxRawScore) {
                    maxRawScore = row[4];
                }
                ParsedDetection parsed = parseRow(row, xScale, yScale, image.cols(), image.rows());
                if (parsed == null) {
                    continue;
                }
                boxes.add(parsed.rect);
                scores.add(parsed.score);
                classIds.add(parsed.classId);
            }

            debug.put("maxRawScoreCol4", maxRawScore == Float.NEGATIVE_INFINITY ? null : maxRawScore);
            debug.put("candidatesAfterParse", boxes.size());

            if (boxes.isEmpty()) {
                debug.put("keptAfterNms", 0);
                debug.put("message", "no boxes parsed before NMS");
                lastDebugInfo = debug;
                return new ArrayList<>();
            }

            float effectiveConf = Math.max(0f, confThreshold);
            MatOfFloat confidences = new MatOfFloat(toPrimitive(scores));
            MatOfRect2d matBoxes = new MatOfRect2d();
            matBoxes.fromList(boxes);
            MatOfInt indices = new MatOfInt();
            Dnn.NMSBoxes(matBoxes, confidences, effectiveConf, nmsThreshold, indices);

            List<DetectionBox> result = new ArrayList<>();
            int[] kept = indices.toArray();
            debug.put("keptAfterNms", kept.length);
            for (int idx : kept) {
                Rect2d rect = boxes.get(idx);
                int cls = classIds.get(idx);
                String className = cls >= 0 && cls < classNames.length ? classNames[cls] : "landslide";
                result.add(new DetectionBox(
                        className,
                        scores.get(idx),
                        (int) Math.round(rect.x),
                        (int) Math.round(rect.y),
                        (int) Math.round(rect.width),
                        (int) Math.round(rect.height)
                ));
            }

            debug.put("returnedBoxes", result.size());
            lastDebugInfo = debug;
            return result;
        } catch (Exception ex) {
            debug.put("error", ex.getMessage());
            lastDebugInfo = debug;
            throw ex;
        } finally {
            release(image, blob, rawOutput, output);
        }
    }

    private ParsedDetection parseRow(float[] row, double xScale, double yScale, int imgW, int imgH) {
        if (row == null || row.length < 5) {
            return null;
        }

        // Format A: [x1, y1, x2, y2, score, classId]
        if (row.length >= 6 && isLikelyXyxyFormat(row)) {
            float score = normalizeScore(row[4]);
            if (score < confThreshold) {
                return null;
            }
            int classId = Math.max(0, Math.round(row[5]));

            double left = clamp(row[0] * xScale, 0, imgW - 1);
            double top = clamp(row[1] * yScale, 0, imgH - 1);
            double right = clamp(row[2] * xScale, left + 1, imgW);
            double bottom = clamp(row[3] * yScale, top + 1, imgH);
            double width = right - left;
            double height = bottom - top;
            if (width <= 1 || height <= 1) {
                return null;
            }
            return new ParsedDetection(new Rect2d(left, top, width, height), score, classId);
        }

        // Format B: [cx, cy, w, h, score] (single-class)
        if (row.length == 5) {
            float score = normalizeScore(row[4]);
            if (score < confThreshold) {
                return null;
            }
            return buildXywhDetection(row[0], row[1], row[2], row[3], score, 0, xScale, yScale, imgW, imgH);
        }

        // Format C: [cx, cy, w, h, obj, cls...] (objectness * class score)
        float objectness = normalizeScore(row[4]);
        int classId = 0;
        float bestClass = row[5];
        for (int c = 6; c < row.length; c++) {
            if (row[c] > bestClass) {
                bestClass = row[c];
                classId = c - 5;
            }
        }
        float score = clamp01(objectness * normalizeScore(bestClass));
        if (score < confThreshold) {
            return null;
        }
        return buildXywhDetection(row[0], row[1], row[2], row[3], score, classId, xScale, yScale, imgW, imgH);
    }

    private ParsedDetection buildXywhDetection(float cxRaw,
                                                float cyRaw,
                                                float wRaw,
                                                float hRaw,
                                                float score,
                                                int classId,
                                                double xScale,
                                                double yScale,
                                                int imgW,
                                                int imgH) {
        double cx = cxRaw * xScale;
        double cy = cyRaw * yScale;
        double w = Math.max(1, wRaw * xScale);
        double h = Math.max(1, hRaw * yScale);

        double left = clamp(cx - w / 2.0, 0, imgW - 1);
        double top = clamp(cy - h / 2.0, 0, imgH - 1);
        double width = Math.min(w, imgW - left);
        double height = Math.min(h, imgH - top);
        if (width <= 1 || height <= 1) {
            return null;
        }
        return new ParsedDetection(new Rect2d(left, top, width, height), score, classId);
    }

    private boolean isLikelyXyxyFormat(float[] row) {
        return row[0] >= 0 && row[1] >= 0 && row[2] > row[0] && row[3] > row[1]
                && row[2] <= inputWidth * 2.0f && row[3] <= inputHeight * 2.0f;
    }

    private float normalizeScore(float score) {
        if (score < 0f || score > 1f) {
            return (float) (1.0 / (1.0 + Math.exp(-score)));
        }
        return score;
    }

    private float clamp01(float value) {
        return (float) clamp(value, 0.0, 1.0);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public boolean isModelReady() {
        return modelReady;
    }

    public Map<String, Object> getLastDebugInfo() {
        return new HashMap<>(lastDebugInfo);
    }

    public void setThresholds(float confThreshold, float nmsThreshold) {
        this.confThreshold = confThreshold;
        this.nmsThreshold = nmsThreshold;
    }

    private Mat normalizeOutput(Mat rawOutput) {
        if (rawOutput.dims() == 3) {
            int dim1 = rawOutput.size(1);
            int dim2 = rawOutput.size(2);
            Mat reshaped = rawOutput.reshape(1, dim1);

            if (dim1 <= 128 && dim2 > dim1) {
                Mat transposed = new Mat();
                Core.transpose(reshaped, transposed);
                return transposed;
            }
            return reshaped;
        }

        if (rawOutput.rows() > 0 && rawOutput.cols() > 0) {
            if (rawOutput.rows() <= 128 && rawOutput.cols() > rawOutput.rows()) {
                Mat transposed = new Mat();
                Core.transpose(rawOutput, transposed);
                return transposed;
            }
            return rawOutput.clone();
        }
        return rawOutput.clone();
    }

    private float[] toPrimitive(List<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private void release(Mat... mats) {
        Arrays.stream(mats).forEach(mat -> {
            if (mat != null) {
                mat.release();
            }
        });
    }

    @Data
    @AllArgsConstructor
    private static class ParsedDetection {
        private Rect2d rect;
        private float score;
        private int classId;
    }

    @Data
    @AllArgsConstructor
    public static class DetectionBox {
        private String category;
        private float confidence;
        private int x;
        private int y;
        private int width;
        private int height;
    }
}