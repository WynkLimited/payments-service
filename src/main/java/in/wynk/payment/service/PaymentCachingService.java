package in.wynk.payment.service;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.data.enums.State;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.SkuMapping;
import in.wynk.payment.core.dao.repository.PaymentMethodDao;
import in.wynk.payment.core.dao.repository.SkuDao;
import in.wynk.payment.core.enums.PaymentGroup;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
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

    @Autowired
    private SkuDao skuDao;
    @Autowired
    private PaymentMethodDao paymentMethodDao;
    @Autowired
    private ISubscriptionServiceManager subscriptionServiceManager;

    private static final Logger logger = LoggerFactory.getLogger(PaymentCachingService.class);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private final Map<PaymentGroup, List<PaymentMethod>> groupedPaymentMethods = new ConcurrentHashMap<>();
    private final Map<Integer, PlanDTO> plans = new ConcurrentHashMap<>();
    private final Map<String, PlanDTO> skuToPlan = new ConcurrentHashMap<>();
    private final Map<String, String> skuToSku = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 30 * 60 * 1000L,  initialDelay = 30 * 60 * 1000L )
    @PostConstruct
    @AnalyseTransaction(name = "refreshInMemoryCache")
    public void init() {
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("cacheLoadInit", true);
        loadPayments();
        loadPlans();
        loadSku();
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
                Map<String, PlanDTO> skuToPlanMap = new HashMap<>();
                for(PlanDTO planDTO: planList){
                    if(MapUtils.isNotEmpty(planDTO.getSku())){
                        for(String sku: planDTO.getSku().values()){
                            skuToPlanMap.putIfAbsent(sku, planDTO);
                        }
                    }
                }
                skuToPlan.clear();
                skuToPlan.putAll(skuToPlanMap);
            } catch (Throwable th) {
                logger.error(APPLICATION_ERROR, "Exception occurred while refreshing offer config cache. Exception: {}", th.getMessage(), th);
                throw th;
            } finally {
                writeLock.unlock();
            }
        }
    }

    private void loadSku() {
        List<SkuMapping> skuMappings = skuDao.findAll();
        if (CollectionUtils.isNotEmpty(skuMappings) && writeLock.tryLock()) {
            try {
                Map<String, String> skuToSkuMap = skuMappings.stream().collect(Collectors.toMap(SkuMapping::getOldSku, SkuMapping::getNewSku));
                skuToSku.clear();
                skuToSku.putAll(skuToSkuMap);
            } catch (Throwable th) {
                logger.error(APPLICATION_ERROR, "Exception occurred while refreshing old sku to new sku cache. Exception: {}", th.getMessage(), th);
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

    public String getNewSku(String oldSku) {
        return skuToSku.get(oldSku);
    }

    public boolean containsSku(String oldSku) {
        return skuToSku.containsKey(oldSku);
    }

    public PlanDTO getPlanFromSku(String sku) {
        return skuToPlan.get(sku);
    }

    public Long validTillDate(int planId) {
        PlanDTO planDTO = getPlan(planId);
        int validity = planDTO.getPeriod().getValidity();
        TimeUnit timeUnit = planDTO.getPeriod().getTimeUnit();
        return System.currentTimeMillis() + timeUnit.toMillis(validity);
    }
}
