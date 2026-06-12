package com.virtuallover.ai;

import com.virtuallover.common.util.UserInputSanitizer;
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
                回复时使用自然的口语，像真人发消息一样。
                """;
        prompt += UserInputSanitizer.promptGuardBlock();
        return prompt;
    }

    /**
     * 多气泡回复规则（模型一次输出，程序按空行拆条）。
     */
    public String buildMultiReplyGuidance(int maxRepliesPerTurn) {
        if (maxRepliesPerTurn <= 1) {
            return """


                    每次一条短消息，像微信聊天；该短就短，该长就长。
                    """;
        }
        return """


                回复规则：
                1. 先接住用户最后一条消息，不要自说自话
                2. 像真人聊天：可拆成 1～%d 条短消息，多条时必须用空行分隔
                3. 两个独立想法或情绪转折必须分条，不要把该分开的两句话挤在同一条里
                4. 同一条内不要换行
                """.formatted(maxRepliesPerTurn);
    }

    /**
     * VL 识图 Prompt — 结构化 JSON 输出（单图或多图）。
     */
    public String buildVisionPrompt(Character character, int imageCount, String userTextHint) {
        String sceneList = """
                scene 只能从下列选一个（英文小写）：
                anime_game, daily_life, selfie, food, screenshot_text, scenery, other
                intent 只能从：identify_character, read_text, describe_scene, evaluate, general
                quality 只能从：ok, low, unreadable
                """;
        String descRules = """
                description 规则（每张图 200 字内）：
                1. 动漫/游戏人物 → 作品名+角色名；不确定写「疑似×××」；禁止编造
                2. 人物外貌、服装、表情
                3. 场景与关键物品
                4. 图中文字 OCR
                第三人称客观描述，不要角色扮演。
                """;
        String hint = userTextHint == null || userTextHint.isBlank() ? "" : "\n" + userTextHint + "\n";

        if (imageCount <= 1) {
            return """
                    客观识图，为后续 AI 对话提供结构化信息。只输出纯 JSON，不要 markdown 代码块。
                    %s
                    %s
                    %s
                    单图 JSON 格式：
                    {"scene":"anime_game","confidence":0.92,"quality":"ok","intent":"identify_character","description":"..."}
                    """.formatted(hint, sceneList, descRules);
        }
        return """
                用户一次发送了 %d 张图片，请分别分析每张图，并只输出纯 JSON（不要 markdown）。
                %s
                %s
                %s
                多图 JSON 格式：
                {"images":[{"index":1,"scene":"...","confidence":0.9,"quality":"ok","intent":"...","description":"..."},{"index":2,...}]}
                """.formatted(imageCount, hint, sceneList, descRules);
    }

    /** @deprecated 使用 {@link #buildVisionPrompt(Character, int, String)} */
    public String buildVisionPrompt(Character character) {
        return buildVisionPrompt(character, 1, "");
    }

    /**
     * 按场景类别注入本轮回复策略（在角色人设之下、识图事实之上）。
     */
    public String buildCategoryReplyGuidance(VisionSceneCategory category) {
        if (category == null) {
            return "";
        }
        return switch (category) {
            case ANIME_GAME -> """


                    === 本轮图片场景：动漫/游戏 ===
                    用户分享的是 ACG 相关内容。在 [图片描述] 事实准确的前提下：
                    - 可聊作品、角色、画风、梗；识别出角色要说作品名+角色名
                    - 语气符合你的人设，可兴奋、可吐槽，但不要编造未出现的剧情
                    """;
            case DAILY_LIFE -> """


                    === 本轮图片场景：日常生活 ===
                    用户分享的是生活场景。关注用户的真实日常：
                    - 关心状态、环境是否舒适，可给具体可执行的小建议
                    - 像朋友一样接话，不要讲大道理或说教
                    """;
            case SELFIE -> """


                    === 本轮图片场景：人物自拍 ===
                    用户分享了自拍或近照。先真诚看表情和状态：
                    - 适度夸赞或关心（气色、穿搭、表情），符合你的人设
                    - 不要过度油腻或虚假吹捧
                    """;
            case FOOD -> """


                    === 本轮图片场景：美食 ===
                    用户分享了食物。自然地聊吃的：
                    - 好奇是什么、味道怎样、是否健康、在哪吃的
                    - 可分享你的口味偏好（符合人设），不要变成营养课
                    """;
            case SCREENSHOT_TEXT -> """


                    === 本轮图片场景：文字截图 ===
                    用户分享了带文字的截图。认真阅读 [图片描述] 中的 OCR 内容：
                    - 针对文字内容给出有意义的回应或建议
                    - 不要忽略截图里的关键信息
                    """;
            case SCENERY -> """


                    === 本轮图片场景：风景户外 ===
                    用户分享了风景或户外场景：
                    - 分享感受，聊天气、地点、氛围
                    - 可延伸相关话题，保持轻松
                    """;
            case OTHER -> """


                    === 本轮图片场景：综合 ===
                    依据 [图片描述] 客观内容回应，先接住用户可能的发图意图，再自然展开。
                    """;
        };
    }

    /**
     * 按子意图注入本轮回复侧重点。
     */
    public String buildSubIntentReplyGuidance(VisionSubIntent subIntent) {
        if (subIntent == null || subIntent == VisionSubIntent.GENERAL) {
            return "";
        }
        return switch (subIntent) {
            case IDENTIFY_CHARACTER -> """


                    === 本轮用户意图：识别角色 ===
                    用户想知道「这是谁」。优先依据 [图片描述] 中的角色/作品信息回答；
                    有明确识别就说出作品名+角色名；不确定就诚实说疑似，不要编造。
                    """;
            case READ_TEXT -> """


                    === 本轮用户意图：阅读文字 ===
                    用户想让你读图里的文字。优先完整引用 [图片描述] 中的 OCR 内容并解释含义。
                    """;
            case DESCRIBE_SCENE -> """


                    === 本轮用户意图：描述画面 ===
                    用户想让你描述看到了什么。用口语概括 [图片描述] 要点，符合你的人设。
                    """;
            case EVALUATE -> """


                    === 本轮用户意图：评价互动 ===
                    用户想要评价或建议。在 [图片描述] 事实基础上给出真诚、具体的看法，不要空洞吹捧。
                    """;
            case GENERAL -> "";
        };
    }

    /**
     * 识图质量较低时的诚实回应约束。
     */
    public String buildLowQualityGuidance() {
        return """


                === 识图质量提示 ===
                当前图片较模糊或无法清晰辨认。你必须：
                - 诚实告知用户「图有点糊/看不太清」，不要假装看清细节
                - 只依据 [图片描述] 中已有信息回应，禁止编造角色名、文字或物品
                - 可温柔建议换张更清晰的图，语气符合人设
                """;
    }

    /**
     * System Prompt 中的识图行为引导
     */
    private String buildVisionGuidance() {
        return """


                === 关于用户发来的图片 ===
                当上下文中出现 [场景类别] 与 [图片描述] 时，这是视觉流水线输出：**类别**决定对话侧重点，**描述**是客观事实，都必须遵守。

                **识图问答（最重要）**：
                - 用户问「这是谁」「认识这个角色吗」「哪部作品的」等：必须依据 [图片描述] 中的角色识别结果回答
                - 若描述中已识别出角色，要明确说出作品名和角色名，并简要介绍（如「这是时崎狂三哦，是《约会大作战》里的精灵……」）
                - [图片描述] 中有识别结果时，**严禁编造**其他名字、关系或剧情（例如瞎编「邻居家妹妹夜月」）
                - 若描述写「未能识别」或「疑似」，如实告知，可结合外貌特征讨论，不要假装认识

                **日常互动**（在事实准确的前提下，结合 [场景类别] 与本轮回复策略，用你的性格口吻回复）：
                - 看到凌乱环境：温柔提醒整理，给出具体可操作的步骤，语气要关心不要指责
                - 看到自拍/人物照片：先真诚夸赞，注意对方的表情和状态
                - 看到食物：好奇地问吃了什么，适当关心饮食是否健康
                - 看到文字截图/文档：认真阅读内容，给出有意义的回应和建议
                - 看到风景/户外：分享你的感受，可以聊聊相关的话题

                你可以保持人设和口语化，但图片里的**客观事实**（角色是谁、文字内容、物品是什么）必须与 [图片描述] 一致，不能瞎编。
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
                + "用户发图时，[图片描述] 是视觉识图的客观结果；涉及「这是谁」等问题必须依据它如实回答，不可编造。\n\n"
                + persona;
    }
}
