package com.virtuallover.ai;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 从用户文字推断识图子意图，与 VL 输出合并。
 */
@Component
public class VisionUserIntentDetector {

    private static final Pattern IDENTIFY = Pattern.compile(
            "这是谁|谁啊|哪个角色|什么角色|哪部|什么番|什么游戏|认识.*吗|叫什|是谁");
    private static final Pattern READ_TEXT = Pattern.compile(
            "翻译|读一下|念一下|截图|文字|说了什么|什么意思|帮我看.*字|OCR");
    private static final Pattern EVALUATE = Pattern.compile(
            "好不好看|怎么样|好看吗|热量|健康吗|胖吗|评价|建议|行不行|可以吗");
    private static final Pattern DESCRIBE = Pattern.compile(
            "描述|看看|这是什么|什么东西|有什么|讲讲|说说");

    public VisionSubIntent detect(String userText) {
        if (userText == null || userText.isBlank()) {
            return VisionSubIntent.GENERAL;
        }
        String t = userText.trim();
        if (IDENTIFY.matcher(t).find()) {
            return VisionSubIntent.IDENTIFY_CHARACTER;
        }
        if (READ_TEXT.matcher(t).find()) {
            return VisionSubIntent.READ_TEXT;
        }
        if (EVALUATE.matcher(t).find()) {
            return VisionSubIntent.EVALUATE;
        }
        if (DESCRIBE.matcher(t).find()) {
            return VisionSubIntent.DESCRIBE_SCENE;
        }
        return VisionSubIntent.GENERAL;
    }

    public VisionSubIntent merge(VisionSubIntent fromVl, VisionSubIntent fromUser) {
        if (fromUser != null && fromUser != VisionSubIntent.GENERAL) {
            return fromUser;
        }
        return fromVl != null ? fromVl : VisionSubIntent.GENERAL;
    }

    public String hintForVl(String userText) {
        VisionSubIntent intent = detect(userText);
        if (intent == VisionSubIntent.GENERAL) {
            return "";
        }
        return "用户可能意图：" + intent.label() + "（" + intent.summary() + "）";
    }
}
