package com.virtuallover.ai;

/**
 * VL 识图 + 场景分类的结构化结果。
 */
public record VisionAnalysisResult(
        VisionSceneCategory category,
        String description,
        String contextBlock,
        double confidence,
        VisionSubIntent subIntent,
        VisionQuality quality,
        int imageCount,
        boolean categoryAdjusted) {

    public VisionAnalysisResult {
        if (subIntent == null) {
            subIntent = VisionSubIntent.GENERAL;
        }
        if (quality == null) {
            quality = VisionQuality.OK;
        }
        if (imageCount < 1) {
            imageCount = 1;
        }
    }

    public static VisionAnalysisResult fallback(String message) {
        String desc = message == null || message.isBlank()
                ? "无法识别这张图片的内容"
                : message.trim();
        return of(VisionSceneCategory.OTHER, desc, VisionSubIntent.GENERAL,
                VisionQuality.UNREADABLE, 0.0, 1, false);
    }

    public static VisionAnalysisResult of(
            VisionSceneCategory category,
            String description,
            VisionSubIntent subIntent,
            VisionQuality quality,
            double confidence,
            int imageCount,
            boolean categoryAdjusted) {
        String block = buildContextBlock(category, description, subIntent, quality, confidence,
                imageCount, categoryAdjusted, null);
        return new VisionAnalysisResult(category, description, block, confidence, subIntent,
                quality, imageCount, categoryAdjusted);
    }

    public static String buildContextBlock(
            VisionSceneCategory category,
            String description,
            VisionSubIntent subIntent,
            VisionQuality quality,
            double confidence,
            int imageCount,
            boolean categoryAdjusted,
            String multiImageBody) {
        StringBuilder sb = new StringBuilder();
        if (imageCount > 1) {
            sb.append("[本轮共").append(imageCount).append("张图片]\n");
        }
        if (multiImageBody != null && !multiImageBody.isBlank()) {
            sb.append(multiImageBody.trim());
        } else {
            appendImageSection(sb, category, description, subIntent, quality, confidence, categoryAdjusted, null);
        }
        if (quality.needsCaution()) {
            sb.append("\n[识图提示] 画面不够清晰，请依据已有信息谨慎回应，勿编造细节。");
        }
        return sb.toString().trim();
    }

    public static void appendImageSection(
            StringBuilder sb,
            VisionSceneCategory category,
            String description,
            VisionSubIntent subIntent,
            VisionQuality quality,
            double confidence,
            boolean categoryAdjusted,
            String imageLabel) {
        if (imageLabel != null && !imageLabel.isBlank()) {
            sb.append("[").append(imageLabel).append("]\n");
        }
        String categoryLabel = category != null ? category.label() : VisionSceneCategory.OTHER.label();
        if (categoryAdjusted) {
            categoryLabel += "（已校正）";
        }
        sb.append("[场景类别] ").append(categoryLabel).append('\n');
        if (subIntent != null && subIntent != VisionSubIntent.GENERAL) {
            sb.append("[子意图] ").append(subIntent.label()).append('\n');
        }
        if (confidence > 0) {
            sb.append("[置信度] ").append(String.format("%.2f", confidence)).append('\n');
        }
        sb.append("[识图质量] ").append(quality.label()).append('\n');
        sb.append("[图片描述] ").append(description != null ? description.trim() : "未能提取具体画面内容");
        sb.append('\n');
    }

    /** 写入 Redis / 注入对话上下文的完整文本 */
    public String toContextBlock() {
        return contextBlock;
    }

    public boolean lowQualityGate() {
        return quality.needsCaution() && confidence < 0.5;
    }
}
