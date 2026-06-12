package com.virtuallover.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 VL 输出：JSON 优先，回退 SCENE 行格式，再经兜底与一致性校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisionResultParser {

    private static final Pattern SCENE_LINE = Pattern.compile(
            "(?im)^\\s*(?:SCENE|场景类别|场景)\\s*[:：]\\s*([\\w\\u4e00-\\u9fa5-]+)\\s*$");

    private final ObjectMapper objectMapper;
    private final VisionCategoryFallback categoryFallback;
    private final VisionCategoryValidator categoryValidator;

    public VisionAnalysisResult parse(String rawVlText) {
        if (rawVlText == null || rawVlText.isBlank()) {
            return VisionAnalysisResult.fallback("无法识别这张图片的内容");
        }

        String text = stripMarkdownFence(rawVlText.trim());
        VisionAnalysisResult fromJson = tryParseJson(text);
        if (fromJson != null) {
            return finalizeResult(fromJson);
        }
        return finalizeResult(parseLegacySceneFormat(text));
    }

    /** 从 Redis 缓存或历史上下文还原结构化结果 */
    public VisionAnalysisResult parseContextBlock(String contextBlock) {
        if (contextBlock == null || contextBlock.isBlank()) {
            return VisionAnalysisResult.fallback("无法识别这张图片的内容");
        }
        String text = contextBlock.trim();
        VisionSceneCategory category = VisionSceneCategory.OTHER;
        VisionSubIntent subIntent = VisionSubIntent.GENERAL;
        VisionQuality quality = VisionQuality.OK;
        double confidence = 0.0;
        boolean adjusted = text.contains("（已校正）");
        int imageCount = 1;

        var countMatcher = Pattern.compile("\\[本轮共(\\d+)张图片\\]").matcher(text);
        if (countMatcher.find()) {
            imageCount = Integer.parseInt(countMatcher.group(1));
        }

        var categoryMatcher = Pattern.compile("\\[场景类别\\]\\s*(.+?)(?:\\n|$)").matcher(text);
        if (categoryMatcher.find()) {
            category = categoryFromLabel(categoryMatcher.group(1).replace("（已校正）", "").trim());
        }

        var intentMatcher = Pattern.compile("\\[子意图\\]\\s*(.+?)(?:\\n|$)").matcher(text);
        if (intentMatcher.find()) {
            subIntent = intentFromLabel(intentMatcher.group(1).trim());
        }

        var confMatcher = Pattern.compile("\\[置信度\\]\\s*([0-9.]+)").matcher(text);
        if (confMatcher.find()) {
            confidence = Double.parseDouble(confMatcher.group(1));
        }

        var qualityMatcher = Pattern.compile("\\[识图质量\\]\\s*(.+?)(?:\\n|$)").matcher(text);
        if (qualityMatcher.find()) {
            quality = qualityFromLabel(qualityMatcher.group(1).trim());
        }

        String description = text;
        var descMatcher = Pattern.compile("\\[图片描述\\]\\s*(.+)", Pattern.DOTALL).matcher(text);
        if (descMatcher.find()) {
            description = descMatcher.group(1).trim();
            int hintIdx = description.indexOf("\n[识图提示]");
            if (hintIdx >= 0) {
                description = description.substring(0, hintIdx).trim();
            }
        }

        if (description.isBlank()) {
            description = "未能提取具体画面内容";
        }
        return new VisionAnalysisResult(category, description, text, confidence, subIntent,
                quality, imageCount, adjusted);
    }

    private VisionAnalysisResult tryParseJson(String text) {
        String json = extractJsonObject(text);
        if (json == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.has("images") && root.get("images").isArray()) {
                return parseMultiImageJson(root.get("images"));
            }
            if (root.has("scene") || root.has("description")) {
                return parseSingleImageJson(root);
            }
        } catch (Exception e) {
            log.debug("VL JSON parse failed, fallback to legacy format");
        }
        return null;
    }

    private VisionAnalysisResult parseSingleImageJson(JsonNode node) {
        VisionSceneCategory category = VisionSceneCategory.fromToken(textOrEmpty(node, "scene"));
        String description = textOrEmpty(node, "description");
        if (description.isBlank()) {
            description = "未能提取具体画面内容";
        }
        VisionSubIntent subIntent = VisionSubIntent.fromToken(textOrEmpty(node, "intent"));
        VisionQuality quality = VisionQuality.fromToken(textOrEmpty(node, "quality"));
        double confidence = node.path("confidence").asDouble(0.0);
        return VisionAnalysisResult.of(category, description, subIntent, quality, confidence, 1, false);
    }

    private VisionAnalysisResult parseMultiImageJson(JsonNode imagesNode) {
        List<ParsedImage> parts = new ArrayList<>();
        int index = 0;
        for (JsonNode img : imagesNode) {
            index++;
            VisionSceneCategory category = VisionSceneCategory.fromToken(textOrEmpty(img, "scene"));
            String description = textOrEmpty(img, "description");
            if (description.isBlank()) {
                description = "未能提取具体画面内容";
            }
            VisionSubIntent subIntent = VisionSubIntent.fromToken(textOrEmpty(img, "intent"));
            VisionQuality quality = VisionQuality.fromToken(textOrEmpty(img, "quality"));
            double confidence = img.path("confidence").asDouble(0.0);
            int imgIndex = img.path("index").asInt(index);
            parts.add(new ParsedImage(imgIndex, category, description, subIntent, quality, confidence));
        }
        if (parts.isEmpty()) {
            return null;
        }

        StringBuilder multiBody = new StringBuilder();
        VisionSceneCategory dominant = parts.get(0).category();
        VisionSubIntent dominantIntent = parts.get(0).subIntent();
        VisionQuality worstQuality = VisionQuality.OK;
        double minConfidence = 1.0;
        StringBuilder combinedDesc = new StringBuilder();

        for (ParsedImage p : parts) {
            VisionAnalysisResult.appendImageSection(
                    multiBody, p.category(), p.description(), p.subIntent(), p.quality(),
                    p.confidence(), false, "图片" + p.index());
            if (p.category() != VisionSceneCategory.OTHER && dominant == VisionSceneCategory.OTHER) {
                dominant = p.category();
            }
            if (p.subIntent() != VisionSubIntent.GENERAL) {
                dominantIntent = p.subIntent();
            }
            if (p.quality().ordinal() > worstQuality.ordinal()) {
                worstQuality = p.quality();
            }
            minConfidence = Math.min(minConfidence, p.confidence());
            if (!combinedDesc.isEmpty()) {
                combinedDesc.append("；");
            }
            combinedDesc.append("图").append(p.index()).append("：").append(p.description());
        }

        String block = VisionAnalysisResult.buildContextBlock(
                dominant, combinedDesc.toString(), dominantIntent, worstQuality,
                minConfidence > 0 ? minConfidence : 0.0, parts.size(), false, multiBody.toString());
        return new VisionAnalysisResult(dominant, combinedDesc.toString(), block,
                minConfidence, dominantIntent, worstQuality, parts.size(), false);
    }

    private VisionAnalysisResult parseLegacySceneFormat(String text) {
        VisionSceneCategory category = VisionSceneCategory.OTHER;
        String description = text;

        Matcher matcher = SCENE_LINE.matcher(text);
        if (matcher.find()) {
            category = VisionSceneCategory.fromToken(matcher.group(1));
            description = text.substring(matcher.end()).trim();
            if (description.startsWith("\n")) {
                description = description.substring(1).trim();
            }
        }
        if (description.isBlank()) {
            description = "未能提取具体画面内容";
        }
        return VisionAnalysisResult.of(category, description, VisionSubIntent.GENERAL,
                VisionQuality.OK, 0.0, 1, false);
    }

    private VisionAnalysisResult finalizeResult(VisionAnalysisResult draft) {
        VisionSceneCategory category = categoryFallback.infer(draft.category(), draft.description());
        boolean fallbackAdjusted = category != draft.category();
        VisionCategoryValidator.ValidationResult validated =
                categoryValidator.validate(category, draft.description());
        category = validated.category();
        boolean adjusted = draft.categoryAdjusted() || fallbackAdjusted || validated.adjusted();

        VisionAnalysisResult refined = VisionAnalysisResult.of(
                category,
                draft.description(),
                draft.subIntent(),
                draft.quality(),
                draft.confidence(),
                draft.imageCount(),
                adjusted);

        if (draft.imageCount() > 1 && draft.contextBlock().contains("[图片1]")) {
            return new VisionAnalysisResult(
                    category,
                    draft.description(),
                    draft.contextBlock(),
                    draft.confidence(),
                    draft.subIntent(),
                    draft.quality(),
                    draft.imageCount(),
                    adjusted);
        }
        return refined;
    }

    private String stripMarkdownFence(String text) {
        if (text.startsWith("```")) {
            int start = text.indexOf('\n');
            int end = text.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return text.substring(start + 1, end).trim();
            }
        }
        return text;
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private String textOrEmpty(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("").trim();
    }

    private VisionSceneCategory categoryFromLabel(String label) {
        if (label == null || label.isBlank()) {
            return VisionSceneCategory.OTHER;
        }
        for (VisionSceneCategory c : VisionSceneCategory.values()) {
            if (c.label().equals(label.trim())) {
                return c;
            }
        }
        return VisionSceneCategory.fromToken(label);
    }

    private VisionSubIntent intentFromLabel(String label) {
        for (VisionSubIntent i : VisionSubIntent.values()) {
            if (i.label().equals(label)) {
                return i;
            }
        }
        return VisionSubIntent.fromToken(label);
    }

    private VisionQuality qualityFromLabel(String label) {
        for (VisionQuality q : VisionQuality.values()) {
            if (q.label().equals(label)) {
                return q;
            }
        }
        return VisionQuality.fromToken(label);
    }

    private record ParsedImage(
            int index,
            VisionSceneCategory category,
            String description,
            VisionSubIntent subIntent,
            VisionQuality quality,
            double confidence) {
    }
}
