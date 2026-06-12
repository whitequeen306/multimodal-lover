package com.virtuallover.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    @JsonIgnore
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private LocalDateTime createdAt;
}
