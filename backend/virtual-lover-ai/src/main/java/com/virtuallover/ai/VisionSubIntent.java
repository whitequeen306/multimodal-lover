package com.virtuallover.ai;

import java.util.Locale;

/**
 * 识图子意图：结合用户文字与画面内容，细化回复策略。
 */
public enum VisionSubIntent {
    IDENTIFY_CHARACTER("识别角色", "用户想确认图中角色/作品是谁"),
    READ_TEXT("阅读文字", "用户想让你读图里的文字或截图内容"),
    DESCRIBE_SCENE("描述画面", "用户想让你描述看到了什么"),
    EVALUATE("评价互动", "用户想让你评价、夸赞或给建议"),
    GENERAL("综合", "未明确子意图时的默认策略");

    private final String label;
    private final String summary;

    VisionSubIntent(String label, String summary) {
        this.label = label;
        this.summary = summary;
    }

    public String label() {
        return label;
    }

    public String summary() {
        return summary;
    }

    public static VisionSubIntent fromToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return GENERAL;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "identify_character", "character", "who", "角色", "识别角色" -> IDENTIFY_CHARACTER;
            case "read_text", "ocr", "text", "文字", "阅读文字" -> READ_TEXT;
            case "describe_scene", "describe", "描述", "描述画面" -> DESCRIBE_SCENE;
            case "evaluate", "comment", "评价", "夸赞", "建议" -> EVALUATE;
            default -> GENERAL;
        };
    }
}
