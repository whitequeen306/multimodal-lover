package com.virtuallover.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@TableName("message")
public class Message {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private String imageUrl;
    private String imageUrls;
    private String visionContext;
    private Integer seq;
    private LocalDateTime createdAt;

    public List<String> getImageUrlList() {
        if (imageUrls != null && !imageUrls.isBlank()) {
            try {
                List<String> list = MAPPER.readValue(imageUrls, new TypeReference<>() {});
                if (list != null && !list.isEmpty()) {
                    return list;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (imageUrl != null && !imageUrl.isBlank()) {
            return List.of(imageUrl);
        }
        return Collections.emptyList();
    }

    public void setImageUrlList(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            this.imageUrls = null;
            this.imageUrl = null;
            return;
        }
        try {
            this.imageUrls = MAPPER.writeValueAsString(urls);
            this.imageUrl = urls.get(0);
        } catch (Exception e) {
            this.imageUrl = urls.get(0);
            this.imageUrls = null;
        }
    }

    public boolean hasImages() {
        return !getImageUrlList().isEmpty();
    }
}
