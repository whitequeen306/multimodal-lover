package com.virtuallover.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("character")
public class Character {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private String name;
    private String avatarUrl;
    private String gender;
    private String personality;
    private String speakingStyle;
    private String backstory;
    private String promptTemplate;
    private Integer isBuiltin;
    private LocalDateTime createdAt;
}
