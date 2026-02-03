package com.qwc.aiappgenerate;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.qwc.aiappgenerate.mapper")
public class AiAppGenerateApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAppGenerateApplication.class, args);
    }

}
