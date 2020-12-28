package in.wynk.payment.service;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.utils.Utils;
import in.wynk.data.enums.State;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.TestingByPassNumbers;
import in.wynk.payment.core.dao.repository.PaymentMethodDao;
import in.wynk.payment.core.dao.repository.TestingByPassNumbersDao;
import in.wynk.payment.core.enums.PaymentGroup;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
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
    private final TestingByPassNumbersDao testingByPassNumbersDao;

    private final Map<PaymentGroup, List<PaymentMethod>> groupedPaymentMethods = new ConcurrentHashMap<>();
    private final Map<Integer, PlanDTO> plans = new ConcurrentHashMap<>();
    private final Map<String, PlanDTO> skuToPlan = new ConcurrentHashMap<>();
    private Set<String> testingByPassNumbers = new HashSet<>();

    public PaymentCachingService(PaymentMethodDao paymentMethodDao, ISubscriptionServiceManager subscriptionServiceManager,TestingByPassNumbersDao testingByPassNumbersDao) {
        this.paymentMethodDao = paymentMethodDao;
        this.subscriptionServiceManager = subscriptionServiceManager;
        this.testingByPassNumbersDao = testingByPassNumbersDao;
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000L,  initialDelay = 30 * 60 * 1000L )
    @PostConstruct
    @AnalyseTransaction(name = "refreshInMemoryCache")
    public void init() {
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("cacheLoadInit", true);
        loadPayments();
        loadPlans();
        loadTestingByPassNumbers();
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

    private void loadTestingByPassNumbers() {
        if(writeLock.tryLock()) {
            try {
                logger.info("I am loading all Testing Config in my cache!!");
                List<TestingByPassNumbers> numbers = testingByPassNumbersDao.findAll();
                testingByPassNumbers.clear();

                testingByPassNumbers = new HashSet<>();
                for(TestingByPassNumbers no : numbers) {
                    testingByPassNumbers.add(Utils.getTenDigitMsisdn(no.getPhoneNo()));
                }
            }
            catch (Throwable th) {
                logger.error(APPLICATION_ERROR, "Error while loading Testing config in the cache, th: {}", th.getMessage(), th);
            }
            finally {
                writeLock.unlock();
            }
        }
    }

    public boolean isTestingByPassNumber(String msisdn) {
        readLock.lock();
        try {
            return testingByPassNumbers.contains(msisdn);
        }
        finally {
            readLock.unlock();
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


    private List<PaymentMethod> getActivePaymentMethods() {
        return paymentMethodDao.findAllByState(State.ACTIVE);
    }


    public PlanDTO getPlan(int planId) {
        return plans.get(planId);
    }

    public PlanDTO getPlanFromSku(String sku){
        return skuToPlan.get(sku);
    }

    public Long validTillDate(int planId) {
        PlanDTO planDTO = getPlan(planId);
        int validity = planDTO.getPeriod().getValidity();
        TimeUnit timeUnit = planDTO.getPeriod().getTimeUnit();
        return System.currentTimeMillis() + timeUnit.toMillis(validity);
    }
}
