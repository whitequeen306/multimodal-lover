package com.virtuallover.common.base;

import lombok.Getter;

@Getter
public enum ErrorCode {
    OK(0, "ok"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    RATE_LIMITED(1005, "请求过于频繁，请稍后再试"),
    CHARACTER_NOT_FOUND(2001, "角色不存在"),
    CONVERSATION_NOT_FOUND(2002, "会话不存在"),
    AI_PROVIDER_ERROR(3001, "AI 服务异常"),
    IMAGE_UPLOAD_FAILED(4001, "图片上传失败"),
    CONTENT_POLICY_VIOLATION(4003, "内容违规");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
