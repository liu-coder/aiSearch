package com.aisearch.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 搜索服务启动入口，负责承载在线检索编排链路。
 */
@SpringBootApplication(scanBasePackages = "com.aisearch")
public class SearchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
