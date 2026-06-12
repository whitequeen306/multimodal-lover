package com.virtuallover.ai;

/**
 * 单张待识图输入。
 */
public record VisionImageInput(byte[] bytes, String contentType, String logRef) {
}
