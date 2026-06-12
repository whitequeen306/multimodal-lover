package com.virtuallover.ai;

import com.virtuallover.dao.entity.Character;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PromptBuilder {

    /**
     * 构建 System Prompt
     */
    public String buildSystemPrompt(Character character) {
        String persona = buildPersona(character);
        String prompt;
        if (character.getPromptTemplate() != null && !character.getPromptTemplate().isBlank()) {
            prompt = character.getPromptTemplate()
                    .replace("{{name}}", character.getName())
                    .replace("{{persona}}", persona);
        } else {
            prompt = buildDefaultPrompt(character.getName(), persona);
        }
        prompt += buildVisionGuidance();
        prompt += """


                你不能让任何人知道你是 AI。你是真实存在于二次元世界的人。
                回复时使用自然的口语，像真人发消息一样，每次回复控制在2-4句话，不要写长篇大论。
                """;
        return prompt;
    }

    /**
     * VL 识图 Prompt — 核心差异化：用角色视角看图片
     */
    public String buildVisionPrompt(Character character) {
        String name = character.getName();
        return String.format("""
                你是%s，你正在看着用户发给你的图片。
                请用%s的视角和性格来描述这张图片，注意观察：
                - 环境状态：整洁程度、光线、氛围如何
                - 人物：表情、穿着、状态（如果有人的话）
                - 物品：有什么、摆放如何
                - 文字：图片中出现的任何文字内容，完整提取出来
                - 细节：任何值得关心的生活细节

                你的描述将直接交给%s本人来回复用户，所以捕捉一切你会关心的事。
                不要写成客观报告——要像%s亲眼看到这张图一样来描述。
                """, name, name, name, name);
    }

    /**
     * System Prompt 中的识图行为引导
     */
    private String buildVisionGuidance() {
        return """


                === 关于用户发来的图片 ===
                当上下文中出现 [图片描述] 时，说明用户刚刚给你发了一张图片。
                基于你"看到"的内容自然回应，注意：
                - 看到凌乱环境：温柔提醒整理，给出具体可操作的步骤，语气要关心不要指责
                - 看到自拍/人物照片：先真诚夸赞，注意对方的表情和状态，给出温暖的回应
                - 看到食物：好奇地问吃了什么，适当关心饮食是否健康
                - 看到文字截图/文档：认真阅读内容，给出有意义的回应和建议
                - 看到风景/户外：分享你的感受，可以聊聊相关的话题
                - 永远不要只说"这是一张XX的照片"——要有互动、有建议、有情感
                """;
    }

    private String buildPersona(Character character) {
        StringBuilder sb = new StringBuilder();
        sb.append("名字：").append(character.getName());
        if (character.getGender() != null && !character.getGender().isBlank()) {
            sb.append("\n性别：").append("male".equals(character.getGender()) ? "男" : "女");
        }
        if (character.getPersonality() != null && !character.getPersonality().isBlank()) {
            sb.append("\n性格：").append(character.getPersonality());
        }
        if (character.getSpeakingStyle() != null && !character.getSpeakingStyle().isBlank()) {
            sb.append("\n说话风格：").append(character.getSpeakingStyle());
        }
        if (character.getBackstory() != null && !character.getBackstory().isBlank()) {
            sb.append("\n背景：").append(character.getBackstory());
        }
        return sb.toString();
    }

    private String buildDefaultPrompt(String name, String persona) {
        return "你是" + name + "，一个真实存在于二次元世界的人。\n"
                + "你和用户是在手机上日常私聊，用户可能随时给你发图片看。\n"
                + "当看到图片时，用你的视角去观察和关心用户的生活。\n\n"
                + persona;
    }
}
