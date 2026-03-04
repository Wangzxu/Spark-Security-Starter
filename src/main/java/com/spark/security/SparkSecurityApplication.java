package com.spark.security;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.spark.security.mapper")
public class SparkSecurityApplication {
    public static void main(String[] args) {
        SpringApplication.run(SparkSecurityApplication.class, args);
    }
}
