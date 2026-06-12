package com.virtuallover.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VisionResultParserTest {

    private VisionResultParser parser;

    @BeforeEach
    void setUp() {
        parser = new VisionResultParser(
                new ObjectMapper(),
                new VisionCategoryFallback(),
                new VisionCategoryValidator());
    }

    @Test
    void parseJsonSingleImage() {
        String json = """
                {"scene":"anime_game","confidence":0.9,"quality":"ok","intent":"identify_character","description":"时崎狂三，长双马尾，《约会大作战》角色"}
                """;
        VisionAnalysisResult result = parser.parse(json);
        assertEquals(VisionSceneCategory.ANIME_GAME, result.category());
        assertEquals(VisionSubIntent.IDENTIFY_CHARACTER, result.subIntent());
        assertEquals(VisionQuality.OK, result.quality());
        assertTrue(result.description().contains("狂三"));
        assertTrue(result.contextBlock().contains("[场景类别]"));
    }

    @Test
    void parseLegacySceneFormat() {
        String text = "SCENE:food\n一份拉面，汤面清晰可见。";
        VisionAnalysisResult result = parser.parse(text);
        assertEquals(VisionSceneCategory.FOOD, result.category());
        assertTrue(result.description().contains("拉面"));
    }

    @Test
    void fallbackInfersAnimeFromDescription() {
        String json = """
                {"scene":"other","confidence":0.5,"quality":"ok","intent":"general","description":"《原神》游戏截图，角色立绘"}
                """;
        VisionAnalysisResult result = parser.parse(json);
        assertEquals(VisionSceneCategory.ANIME_GAME, result.category());
    }

    @Test
    void parseMultiImageJson() {
        String json = """
                {"images":[{"index":1,"scene":"food","confidence":0.8,"quality":"ok","intent":"describe_scene","description":"一碗拉面"},{"index":2,"scene":"scenery","confidence":0.85,"quality":"ok","intent":"describe_scene","description":"海边日落"}]}
                """;
        VisionAnalysisResult result = parser.parse(json);
        assertEquals(2, result.imageCount());
        assertTrue(result.contextBlock().contains("[图片1]"));
        assertTrue(result.contextBlock().contains("[图片2]"));
    }

    @Test
    void parseContextBlockRoundTrip() {
        VisionAnalysisResult original = VisionAnalysisResult.of(
                VisionSceneCategory.SELFIE,
                "人物自拍，微笑",
                VisionSubIntent.EVALUATE,
                VisionQuality.LOW,
                0.4,
                1,
                false);
        VisionAnalysisResult restored = parser.parseContextBlock(original.toContextBlock());
        assertEquals(VisionSceneCategory.SELFIE, restored.category());
        assertEquals(VisionSubIntent.EVALUATE, restored.subIntent());
        assertEquals(VisionQuality.LOW, restored.quality());
    }
}
