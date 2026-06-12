package com.virtuallover.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * 送 VL 前缩边压缩，降低延迟与 token 消耗。
 */
@Slf4j
@Component
public class ImagePreprocessor {

    private static final int MAX_EDGE = 1280;
    private static final float JPEG_QUALITY = 0.85f;

    public byte[] preprocess(byte[] input, String contentType) {
        if (input == null || input.length == 0) {
            return input;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(input));
            if (source == null) {
                return input;
            }
            int w = source.getWidth();
            int h = source.getHeight();
            if (w <= MAX_EDGE && h <= MAX_EDGE && input.length <= 512 * 1024) {
                return input;
            }

            double scale = 1.0;
            int maxDim = Math.max(w, h);
            if (maxDim > MAX_EDGE) {
                scale = (double) MAX_EDGE / maxDim;
            }
            int targetW = Math.max(1, (int) Math.round(w * scale));
            int targetH = Math.max(1, (int) Math.round(h * scale));

            BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, targetW, targetH, null);
            g.dispose();

            boolean usePng = contentType != null && contentType.contains("png") && source.getColorModel().hasAlpha();
            if (usePng) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(scaled, "png", out);
                return out.toByteArray();
            }
            return encodeJpeg(scaled);
        } catch (Exception e) {
            log.debug("Image preprocess skipped, reason={}", e.getMessage());
            return input;
        }
    }

    public String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "image/jpeg";
        }
        if (contentType.contains("png")) {
            return "image/png";
        }
        if (contentType.contains("gif")) {
            return "image/jpeg";
        }
        if (contentType.contains("webp")) {
            return "image/jpeg";
        }
        return "image/jpeg";
    }

    private byte[] encodeJpeg(BufferedImage image) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpeg", out);
            return out.toByteArray();
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.setOutput(new MemoryCacheImageOutputStream(out));
        writer.write(null, new IIOImage(image, null, null), param);
        writer.dispose();
        return out.toByteArray();
    }
}
