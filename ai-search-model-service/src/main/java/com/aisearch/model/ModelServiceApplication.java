package com.aisearch.model;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 模型服务启动入口，后续统一封装国内模型供应商调用。
 */
@SpringBootApplication(scanBasePackages = "com.aisearch")
public class ModelServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ModelServiceApplication.class, args);
    }
}
