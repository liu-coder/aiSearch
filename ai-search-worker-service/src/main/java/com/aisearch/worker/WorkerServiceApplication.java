package com.aisearch.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 离线 worker 服务启动入口，承载视频处理状态机和索引构建任务。
 */
@SpringBootApplication(scanBasePackages = "com.aisearch")
@EnableScheduling
public class WorkerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkerServiceApplication.class, args);
    }
}
