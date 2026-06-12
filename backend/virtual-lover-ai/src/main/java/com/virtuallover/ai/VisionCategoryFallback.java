package com.virtuallover.ai;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * VL 未输出 SCENE 或判为 other 时，从描述关键词推断场景类别。
 */
@Component
public class VisionCategoryFallback {

    private static final Pattern ACG_SIGNAL = Pattern.compile(
            "动漫|番剧|游戏|立绘|CG|二次元|《[^》]+》|手办|cos|原神|崩坏|明日方舟");
    private static final Pattern SELFIE_SIGNAL = Pattern.compile("自拍|对镜|镜面|证件照|近照|人脸特写");
    private static final Pattern FOOD_SIGNAL = Pattern.compile("食物|餐饮|美食|料理|外卖|奶茶|咖啡|蛋糕|火锅|早餐|午餐|晚餐");
    private static final Pattern SCREENSHOT_SIGNAL = Pattern.compile("截图|聊天记录|文档|网页|字幕|界面|对话框|OCR|文字内容");
    private static final Pattern SCENERY_SIGNAL = Pattern.compile("风景|户外|旅行|天空|海边|山川|公园|夜景|日落");

    public VisionSceneCategory infer(VisionSceneCategory current, String description) {
        if (description == null || description.isBlank()) {
            return current != null ? current : VisionSceneCategory.OTHER;
        }
        if (current != null && current != VisionSceneCategory.OTHER) {
            return current;
        }
        String text = description.toLowerCase(Locale.ROOT);
        if (ACG_SIGNAL.matcher(description).find()) {
            return VisionSceneCategory.ANIME_GAME;
        }
        if (SELFIE_SIGNAL.matcher(description).find()) {
            return VisionSceneCategory.SELFIE;
        }
        if (FOOD_SIGNAL.matcher(description).find()) {
            return VisionSceneCategory.FOOD;
        }
        if (SCREENSHOT_SIGNAL.matcher(description).find() || countCjkChars(description) > 80) {
            return VisionSceneCategory.SCREENSHOT_TEXT;
        }
        if (SCENERY_SIGNAL.matcher(description).find()) {
            return VisionSceneCategory.SCENERY;
        }
        if (text.contains("房间") || text.contains("桌面") || text.contains("卧室") || text.contains("日常")) {
            return VisionSceneCategory.DAILY_LIFE;
        }
        return current != null ? current : VisionSceneCategory.OTHER;
    }

    private int countCjkChars(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.UnicodeScript.of(text.charAt(i)) == Character.UnicodeScript.HAN) {
                count++;
            }
        }
        return count;
    }
}
