package com.virtuallover.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    scanBasePackages = "com.virtuallover",
    excludeName = {
        "org.springframework.ai.model.openai.autoconfigure.OpenAiAutoConfiguration"
    }
)
@MapperScan("com.virtuallover.dao.mapper")
public class MultiModalLoverApplication {
    public static void main(String[] args) {
        SpringApplication.run(MultiModalLoverApplication.class, args);
    }
}
