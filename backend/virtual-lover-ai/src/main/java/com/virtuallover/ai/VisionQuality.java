package com.virtuallover.ai;

import java.util.Locale;

/**
 * VL 识图质量评估。
 */
public enum VisionQuality {
    OK("清晰"),
    LOW("较模糊"),
    UNREADABLE("无法辨认");

    private final String label;

    VisionQuality(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean needsCaution() {
        return this == LOW || this == UNREADABLE;
    }

    public static VisionQuality fromToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return OK;
        }
        String n = raw.trim().toLowerCase(Locale.ROOT);
        return switch (n) {
            case "low", "blur", "blurry", "模糊", "较模糊" -> LOW;
            case "unreadable", "unrecognizable", "无法辨认", "无法识别" -> UNREADABLE;
            default -> OK;
        };
    }
}
