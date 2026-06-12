package com.virtuallover.ai;

import java.util.Locale;

/**
 * 识图后的场景分类，用于驱动不同的对话策略。
 */
public enum VisionSceneCategory {
    ANIME_GAME("动漫/游戏画面", "用户发来的是动漫、游戏或二次元相关画面"),
    DAILY_LIFE("日常生活", "用户发来的是日常起居、室内环境、生活杂物等"),
    SELFIE("人物自拍", "用户发来的是自拍或人物近照"),
    FOOD("美食餐饮", "用户发来的是食物、餐饮相关"),
    SCREENSHOT_TEXT("文字截图", "用户发来的是带文字的截图、文档、聊天记录等"),
    SCENERY("风景户外", "用户发来的是风景、户外、旅行场景"),
    OTHER("其他", "未能明确归类的图片");

    private final String label;
    private final String summary;

    VisionSceneCategory(String label, String summary) {
        this.label = label;
        this.summary = summary;
    }

    public String label() {
        return label;
    }

    public String summary() {
        return summary;
    }

    public static VisionSceneCategory fromToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "anime_game", "anime", "game", "acg", "二次元" -> ANIME_GAME;
            case "daily_life", "daily", "life", "生活" -> DAILY_LIFE;
            case "selfie", "portrait", "人物", "自拍" -> SELFIE;
            case "food", "美食", "餐饮" -> FOOD;
            case "screenshot_text", "screenshot", "text", "文字", "截图" -> SCREENSHOT_TEXT;
            case "scenery", "landscape", "风景", "户外" -> SCENERY;
            default -> OTHER;
        };
    }
}
