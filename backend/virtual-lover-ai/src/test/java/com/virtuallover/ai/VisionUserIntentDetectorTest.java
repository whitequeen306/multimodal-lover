package com.virtuallover.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VisionUserIntentDetectorTest {

    private final VisionUserIntentDetector detector = new VisionUserIntentDetector();

    @Test
    void detectIdentifyCharacter() {
        assertEquals(VisionSubIntent.IDENTIFY_CHARACTER, detector.detect("这是谁啊？"));
        assertEquals(VisionSubIntent.IDENTIFY_CHARACTER, detector.detect("认识这个角色吗"));
    }

    @Test
    void detectReadText() {
        assertEquals(VisionSubIntent.READ_TEXT, detector.detect("帮我读一下截图里的文字"));
        assertEquals(VisionSubIntent.READ_TEXT, detector.detect("翻译一下"));
    }

    @Test
    void detectEvaluate() {
        assertEquals(VisionSubIntent.EVALUATE, detector.detect("好不好看"));
        assertEquals(VisionSubIntent.EVALUATE, detector.detect("这热量高吗"));
    }

    @Test
    void mergePrefersUserIntent() {
        assertEquals(VisionSubIntent.IDENTIFY_CHARACTER,
                detector.merge(VisionSubIntent.GENERAL, VisionSubIntent.IDENTIFY_CHARACTER));
        assertEquals(VisionSubIntent.READ_TEXT,
                detector.merge(VisionSubIntent.DESCRIBE_SCENE, VisionSubIntent.READ_TEXT));
    }
}
