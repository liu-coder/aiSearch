package com.aisearch.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关服务启动入口，统一暴露搜索、视频、worker 和模型服务 API。
 */
@SpringBootApplication(scanBasePackages = "com.aisearch")
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
