package in.wynk.payment.service;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.ICacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.IN_MEMORY_CACHE_CRON;
import static in.wynk.common.constant.CacheBeanNameConstants.PLAN_DTO;
import static in.wynk.exception.WynkErrorType.UT025;
import static in.wynk.logging.BaseLoggingMarkers.APPLICATION_ERROR;

@Slf4j
@RequiredArgsConstructor
@Service(value = PLAN_DTO)
public class PlanDtoCachingService implements ICacheService<PlanDTO, Integer> {

    private final Map<Integer, PlanDTO> planDTOMap = new ConcurrentHashMap<>();
    private final ISubscriptionServiceManager subscriptionServiceManager;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock writeLock = lock.writeLock();

    @PostConstruct
    @AnalyseTransaction(name = "refreshInMemoryCachePlanDto")
    @Scheduled(fixedDelay = IN_MEMORY_CACHE_CRON, initialDelay = IN_MEMORY_CACHE_CRON)
    public void init() {
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("cacheLoadInit", true);
        loadPlans();
        AnalyticService.update("cacheLoadCompleted", true);
    }

    private void loadPlans() {
        List<PlanDTO> planList = subscriptionServiceManager.getPlans();
        if (CollectionUtils.isNotEmpty(planList) && writeLock.tryLock()) {
            try {
                Map<Integer, PlanDTO> temp = planList.stream().collect(Collectors.toMap(PlanDTO::getId, Function.identity()));
                planDTOMap.clear();
                planDTOMap.putAll(temp);
            } catch (Throwable th) {
                log.error(APPLICATION_ERROR, "Exception occurred while refreshing planDto config cache. Exception: {}", th.getMessage(), th);
                throw th;
            } finally {
                writeLock.unlock();
            }
        }
    }

    @Override
    public PlanDTO get(Integer key) {
        return planDTOMap.get(key);
    }

    @Override
    public PlanDTO save(PlanDTO item) {
        throw new WynkRuntimeException(UT025);
    }

    @Override
    public Collection<PlanDTO> getAll() {
        return planDTOMap.values();
    }

    @Override
    public boolean containsKey(Integer key) {
        return planDTOMap.containsKey(key);
    }

}