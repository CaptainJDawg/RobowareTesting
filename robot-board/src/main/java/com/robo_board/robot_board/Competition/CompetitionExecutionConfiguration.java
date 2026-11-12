package com.robo_board.robot_board.competition;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class CompetitionExecutionConfiguration {

    @Bean("competitionExecutor")
    public ThreadPoolTaskExecutor competitionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("competition-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }
}
