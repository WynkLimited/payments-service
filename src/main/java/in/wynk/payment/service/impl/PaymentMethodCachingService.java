package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.data.enums.State;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.repository.IPaymentMethodDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.IN_MEMORY_CACHE_CRON;
import static in.wynk.common.constant.CacheBeanNameConstants.PAYMENT_METHOD;
import static in.wynk.logging.BaseLoggingMarkers.APPLICATION_ERROR;

@Slf4j
@RequiredArgsConstructor
@Service(value = PAYMENT_METHOD)
public class PaymentMethodCachingService implements IEntityCacheService<PaymentMethod, String> {

    private final Map<String, PaymentMethod> paymentMethodMap = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock writeLock = lock.writeLock();
    private final IPaymentMethodDao dao;

    @PostConstruct
    @AnalyseTransaction(name = "refreshInMemoryCachePaymentMethod")
    @Scheduled(fixedDelay = IN_MEMORY_CACHE_CRON, initialDelay = IN_MEMORY_CACHE_CRON)
    public void init() {
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("cacheLoadInit", true);
        loadWynkServices();
        AnalyticService.update("cacheLoadCompleted", true);
    }

    private void loadWynkServices() {
        Collection<PaymentMethod> allMethods = getAllByState(State.ACTIVE);
        if (CollectionUtils.isNotEmpty(allMethods) && writeLock.tryLock()) {
            try {
                Map<String, PaymentMethod> temp = allMethods.stream().collect(Collectors.toMap(PaymentMethod::getId, Function.identity()));
                paymentMethodMap.putAll(temp);
            } catch (Throwable th) {
                log.error(APPLICATION_ERROR, "Exception occurred while refreshing paymentMethod cache. Exception: {}", th.getMessage(), th);
                throw th;
            } finally {
                writeLock.unlock();
            }
        }
    }

    @Override
    public PaymentMethod save(PaymentMethod paymentMethod) {
        return dao.save(paymentMethod);
    }

    @Override
    public PaymentMethod get(String key) {
        return paymentMethodMap.get(key);
    }

    @Override
    public Collection<PaymentMethod> getAll() {
        return paymentMethodMap.values();
    }

    @Override
    public boolean containsKey(String key) {
        return paymentMethodMap.containsKey(key);
    }

    @Override
    public Collection<PaymentMethod> getAllByState(State state) {
        return dao.findAllByState(state);
    }

}