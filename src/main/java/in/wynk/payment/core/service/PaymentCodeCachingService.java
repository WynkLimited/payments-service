package in.wynk.payment.core.service;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.data.enums.State;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentCode;
import in.wynk.payment.core.dao.repository.IPaymentCodeDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
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
import static in.wynk.common.constant.CacheBeanNameConstants.PAYMENT_CODE;
import static in.wynk.logging.BaseLoggingMarkers.APPLICATION_ERROR;

@Slf4j
@RequiredArgsConstructor
@Service(value = PAYMENT_CODE)
public class PaymentCodeCachingService implements IEntityCacheService<PaymentCode, String> {

    private final Map<String, PaymentCode> paymentCodeMap = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock writeLock = lock.writeLock();
    private final IPaymentCodeDao paymentCodeDao;

    public static PaymentCode getFromCode(String codeStr) {
        for (PaymentCode paymentCode : (BeanLocatorFactory.getBean(PaymentCodeCachingService.class)).getAll())
            if (StringUtils.equalsIgnoreCase(codeStr, paymentCode.getCode())) return paymentCode;
        throw new WynkRuntimeException(PaymentErrorType.PAY001);
    }

    public static PaymentCode getFromPaymentCode(String paymentCode) {
        for (PaymentCode code : (BeanLocatorFactory.getBean(PaymentCodeCachingService.class)).getAll())
            if (StringUtils.equalsIgnoreCase(paymentCode, code.getId())) return code;
        throw new WynkRuntimeException(PaymentErrorType.PAY001);
    }

    @PostConstruct
    @AnalyseTransaction(name = "refreshInMemoryCachePaymentCode")
    @Scheduled(fixedDelay = IN_MEMORY_CACHE_CRON, initialDelay = IN_MEMORY_CACHE_CRON)
    public void init() {
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("cacheLoadInit", true);
        loadPlans();
        AnalyticService.update("cacheLoadCompleted", true);
    }

    private void loadPlans() {
        Collection<PaymentCode> paymentCodeCollection = this.getAllByState(State.ACTIVE);
        if (CollectionUtils.isNotEmpty(paymentCodeCollection) && writeLock.tryLock()) {
            try {
                Map<String, PaymentCode> temp = paymentCodeCollection.stream().collect(Collectors.toMap(PaymentCode::getId, Function.identity()));
                paymentCodeMap.clear();
                paymentCodeMap.putAll(temp);
            } catch (Throwable th) {
                log.error(APPLICATION_ERROR, "Exception occurred while refreshing payment code config cache. Exception: {}", th.getMessage(), th);
                throw th;
            } finally {
                writeLock.unlock();
            }
        }
    }

    @Override
    public PaymentCode get(String key) {
        return paymentCodeMap.get(key);
    }

    @Override
    public PaymentCode save(PaymentCode item) {
        return paymentCodeDao.save(item);
    }

    @Override
    public Collection<PaymentCode> getAll() {
        return paymentCodeMap.values();
    }

    @Override
    public boolean containsKey(String key) {
        return paymentCodeMap.containsKey(key);
    }

    @Override
    public Collection<PaymentCode> getAllByState(State state) {
        return paymentCodeDao.findAllByState(state);
    }

}