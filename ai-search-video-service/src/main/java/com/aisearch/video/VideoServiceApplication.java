package com.aisearch.video;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 视频服务启动入口，负责视频资产、上传和后续入库事件边界。
 */
@SpringBootApplication(scanBasePackages = "com.aisearch")
@EnableScheduling
public class VideoServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(VideoServiceApplication.class, args);
    }
}
