package com.virtuallover.common.base;

import lombok.Getter;

@Getter
public enum ErrorCode {
    OK(0, "ok"),
    BAD_REQUEST(400, "填写信息有误，请检查后重试"),
    UNAUTHORIZED(401, "请先登录"),
    FORBIDDEN(403, "你没有权限进行此操作"),
    NOT_FOUND(404, "内容不存在或已被删除"),
    RATE_LIMITED(1005, "操作太频繁了，请稍后再试"),
    CHARACTER_NOT_FOUND(2001, "角色不存在或已被删除"),
    CONVERSATION_NOT_FOUND(2002, "会话不存在或已被删除"),
    AI_PROVIDER_ERROR(3001, "AI 暂时无法回应，请稍后重试"),
    IMAGE_UPLOAD_FAILED(4001, "图片上传失败，请稍后重试"),
    CONTENT_POLICY_VIOLATION(4003, "内容包含不合适的信息，请修改后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
