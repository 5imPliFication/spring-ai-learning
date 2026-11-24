package com.example.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dedicated, bounded pool for AI calls. Model calls block for seconds, so they must not run on the
 * shared ForkJoinPool. When the queue is full, submissions are rejected and the user gets a "busy" reply.
 */
@Configuration
public class AiExecutorConfig {

    public static final String AI_EXECUTOR = "aiExecutor";

    @Bean(name = AI_EXECUTOR)
    public ThreadPoolTaskExecutor aiExecutor(@Value("${app.ai.executor.threads:4}") int threads,
                                             @Value("${app.ai.executor.queue-capacity:50}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ai-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
