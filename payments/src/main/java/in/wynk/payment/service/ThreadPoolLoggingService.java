package in.wynk.payment.service;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import static in.wynk.common.constant.BaseConstants.IN_MEMORY_CACHE_CRON;

@Slf4j
@Component
public class ThreadPoolLoggingService {

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @AnalyseTransaction(name = "logThreadPoolStats")
    @Scheduled(fixedDelay = IN_MEMORY_CACHE_CRON, initialDelay = IN_MEMORY_CACHE_CRON)
    public void logThreadPoolStats() {
        int activeCount = taskExecutor.getActiveCount();
        AnalyticService.update("activeCount", activeCount);
        int corePoolSize = taskExecutor.getCorePoolSize();
        AnalyticService.update("corePoolSize", corePoolSize);
        int maxPoolSize = taskExecutor.getMaxPoolSize();
        AnalyticService.update("maxPoolSize", maxPoolSize);
        int poolSize = taskExecutor.getPoolSize();
        AnalyticService.update("poolSize", poolSize);
        int queueSize = taskExecutor.getThreadPoolExecutor().getQueue().size();
        AnalyticService.update("queueSize", queueSize);
        log.info("Active Threads: {}, Core Pool Size: {}, Max Pool Size: {}, Current Pool Size: {}, Queue Size: {}",
                activeCount, corePoolSize, maxPoolSize, poolSize, queueSize);
    }
}
