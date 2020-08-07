package in.wynk.payment.service;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.enums.PaymentGroup;
import in.wynk.commons.enums.State;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.repository.PaymentMethodDao;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static in.wynk.logging.BaseLoggingMarkers.APPLICATION_ERROR;

@Service
@Getter
public class PaymentCachingService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCachingService.class);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private final PaymentMethodDao paymentMethodDao;
    private final ISubscriptionServiceManager subscriptionServiceManager;

    private final Map<PaymentGroup, List<PaymentMethod>> groupedPaymentMethods = new ConcurrentHashMap<>();
    private final Map<Integer, PlanDTO> plans = new ConcurrentHashMap<>();

    public PaymentCachingService(PaymentMethodDao paymentMethodDao, ISubscriptionServiceManager subscriptionServiceManager) {
        this.paymentMethodDao = paymentMethodDao;
        this.subscriptionServiceManager = subscriptionServiceManager;
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000L)
    @PostConstruct
    @AnalyseTransaction(name = "refreshInMemoryCache")
    public void init() {
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("cacheLoadInit", true);
        loadPayments();
        loadPlans();
        AnalyticService.update("cacheLoadCompleted", true);
    }

    private void loadPayments() {
        List<PaymentMethod> activePaymentMethods = getActivePaymentMethods();
        if (CollectionUtils.isNotEmpty(activePaymentMethods) && writeLock.tryLock()) {
            Map<PaymentGroup, List<PaymentMethod>> methods = new ConcurrentHashMap<>();
            try {
                for (PaymentMethod method : activePaymentMethods) {
                    List<PaymentMethod> paymentMethods = methods.getOrDefault(method.getGroup(), new ArrayList<>());
                    paymentMethods.add(method);
                    methods.put(method.getGroup(), paymentMethods);
                }
                groupedPaymentMethods.clear();
                groupedPaymentMethods.putAll(methods);
            } catch (Throwable th) {
                logger.error(APPLICATION_ERROR, "Exception occurred while refreshing offer config cache. Exception: {}", th.getMessage(), th);
                throw th;
            } finally {
                writeLock.unlock();
            }
        }
    }

    private void loadPlans() {
        List<PlanDTO> planList = subscriptionServiceManager.getPlans();
        if (CollectionUtils.isNotEmpty(planList) && writeLock.tryLock()) {
            try {
                Map<Integer, PlanDTO> planDTOMap = planList.stream().collect(Collectors.toMap(PlanDTO::getId, Function.identity()));
                plans.clear();
                plans.putAll(planDTOMap);

            } catch (Throwable th) {
                logger.error(APPLICATION_ERROR, "Exception occurred while refreshing offer config cache. Exception: {}", th.getMessage(), th);
                throw th;
            } finally {
                writeLock.unlock();
            }
        }
    }


    private List<PaymentMethod> getActivePaymentMethods() {
        return paymentMethodDao.findAllByState(State.ACTIVE);
    }


    public PlanDTO getPlan(int planId) {
        return plans.get(planId);
    }

    public Long validTillDate(int planId) {
        PlanDTO planDTO = getPlan(planId);
        int validity = planDTO.getPeriod().getValidity();
        TimeUnit timeUnit = planDTO.getPeriod().getTimeUnit();
        return System.currentTimeMillis() + timeUnit.toMillis(validity);
    }
}
