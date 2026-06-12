package com.virtuallover.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 校验场景类别与描述是否一致，必要时校正。
 */
@Slf4j
@Component
public class VisionCategoryValidator {

    private static final Pattern ACG_STRONG = Pattern.compile(
            "《[^》]+》|角色|立绘|番剧|游戏截图|二次元|CG|原神|崩坏|明日方舟|动漫人物");
    private static final Pattern FOOD_STRONG = Pattern.compile(
            "食物|餐饮|美食|料理|外卖|奶茶|咖啡|蛋糕|火锅|一盘|一碗|一杯");

    public record ValidationResult(VisionSceneCategory category, boolean adjusted) {
    }

    public ValidationResult validate(VisionSceneCategory category, String description) {
        if (category == null) {
            category = VisionSceneCategory.OTHER;
        }
        if (description == null || description.isBlank()) {
            return new ValidationResult(category, false);
        }

        boolean acg = ACG_STRONG.matcher(description).find();
        boolean food = FOOD_STRONG.matcher(description).find();

        if (acg && (category == VisionSceneCategory.FOOD
                || category == VisionSceneCategory.DAILY_LIFE
                || category == VisionSceneCategory.SCENERY)) {
            log.warn("Vision category adjusted: {} -> anime_game (ACG signals in description)", category);
            return new ValidationResult(VisionSceneCategory.ANIME_GAME, true);
        }
        if (food && !acg && category == VisionSceneCategory.ANIME_GAME) {
            log.warn("Vision category adjusted: anime_game -> food (food signals in description)");
            return new ValidationResult(VisionSceneCategory.FOOD, true);
        }
        if (category == VisionSceneCategory.SELFIE && acg && !description.contains("自拍")) {
            log.warn("Vision category adjusted: selfie -> anime_game (character portrait detected)");
            return new ValidationResult(VisionSceneCategory.ANIME_GAME, true);
        }
        return new ValidationResult(category, false);
    }
}
