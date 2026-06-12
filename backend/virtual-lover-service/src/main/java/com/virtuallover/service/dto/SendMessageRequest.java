package com.virtuallover.service.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SendMessageRequest {
    private String content;
    /** @deprecated 使用 imageUrls */
    private String imageUrl;
    @Size(max = 2, message = "一次最多发送 2 张图片")
    private List<String> imageUrls;

    @AssertTrue(message = "请输入消息内容或上传图片")
    public boolean isContentOrImagePresent() {
        return hasText(content) || !resolveImageUrls().isEmpty();
    }

    public List<String> resolveImageUrls() {
        List<String> urls = new ArrayList<>();
        if (imageUrls != null) {
            for (String url : imageUrls) {
                if (hasText(url)) {
                    urls.add(url.trim());
                }
            }
        }
        if (urls.isEmpty() && hasText(imageUrl)) {
            urls.add(imageUrl.trim());
        }
        if (urls.size() > 2) {
            return urls.subList(0, 2);
        }
        return urls;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
