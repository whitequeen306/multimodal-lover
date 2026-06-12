ALTER TABLE `message`
    ADD COLUMN `image_urls` JSON NULL COMMENT '最多2张，JSON数组' AFTER `image_url`,
    ADD COLUMN `vision_context` TEXT NULL COMMENT 'VL结构化摘要，供历史上下文' AFTER `image_urls`;
