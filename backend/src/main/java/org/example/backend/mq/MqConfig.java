package org.example.backend.mq;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 打开 @Scheduled（消费者轮询与超时消息认领都依赖它） */
@Configuration
@EnableScheduling
public class MqConfig {
}
