package in.wynk.payment.service;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.data.enums.State;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.SkuMapping;
import in.wynk.payment.core.dao.repository.IPaymentGroupDao;
import in.wynk.payment.core.dao.repository.PaymentMethodDao;
import in.wynk.payment.core.dao.repository.SkuDao;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.math.NumberUtils;
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

    private final Map<Integer, OfferDTO> offers = new ConcurrentHashMap<>();
    private final Map<String, PartnerDTO> partners = new ConcurrentHashMap<>();
    @Autowired
    private SkuDao skuDao;

    private static final Logger logger = LoggerFactory.getLogger(PaymentCachingService.class);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private final Map<String, PaymentGroup> paymentGroups = new ConcurrentHashMap<>();
    private final Map<String, List<PaymentMethod>> groupedPaymentMethods = new ConcurrentHashMap<>();
    private final Map<Integer, PlanDTO> plans = new ConcurrentHashMap<>();
    @Autowired
    private PaymentMethodDao paymentMethodDao;
    @Autowired
    private IPaymentGroupDao paymentGroupDao;
    @Autowired
    private ISubscriptionServiceManager subscriptionServiceManager;
    private final Map<String, PlanDTO> skuToPlan = new ConcurrentHashMap<>();
    private final Map<String, String> skuToSku = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 30 * 60 * 1000L, initialDelay = 30 * 60 * 1000L)
    @PostConstruct
    @AnalyseTransaction(name = "refreshInMemoryCache")
    public void init() {
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("cacheLoadInit", true);
        loadPayments();
        loadPlans();
        loadSku();
        loadOffers();
        loadPartners();
        AnalyticService.update("cacheLoadCompleted", true);
    }

    private void loadPayments() {
        final Map<String, PaymentGroup> groupsMap = paymentGroupDao.findAllByState(State.ACTIVE).stream().collect(Collectors.toConcurrentMap(PaymentGroup::getId, Function.identity()));
        if (MapUtils.isNotEmpty(groupsMap) && writeLock.tryLock()) {
            Map<String, List<PaymentMethod>> newPaymentMethods = new ConcurrentHashMap<>();
            try {
                List<PaymentMethod> paymentMethods = paymentMethodDao.findByGroupInAndState(groupsMap.keySet(), State.ACTIVE);
                for (PaymentMethod method : paymentMethods) {
                    if(groupsMap.containsKey(method.getGroup())) {
                        List<PaymentMethod> paymentMethodsInternal = newPaymentMethods.getOrDefault(method.getGroup(), new ArrayList<>());
                        paymentMethodsInternal.add(method);
                        newPaymentMethods.put(method.getGroup(), paymentMethodsInternal);
                    }
                }
                for(String group : groupsMap.keySet()) {
                   if(!newPaymentMethods.containsKey(group))
                       groupsMap.remove(group);
                }
                paymentGroups.clear();
                paymentGroups.putAll(groupsMap);
                groupedPaymentMethods.clear();
                groupedPaymentMethods.putAll(newPaymentMethods);
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

    private void loadOffers() {
        List<OfferDTO> offerList = subscriptionServiceManager.getOffers();
        if (CollectionUtils.isNotEmpty(offerList) && writeLock.tryLock()) {
            try {
                Map<Integer, OfferDTO> offerMap = offerList.stream().collect(Collectors.toMap(OfferDTO::getId, Function.identity()));
                offers.clear();
                offers.putAll(offerMap);
            } catch (Throwable th) {
                logger.error(APPLICATION_ERROR, "Exception occurred while refreshing offer config cache. Exception: {}", th.getMessage(), th);
                throw th;
            } finally {
                writeLock.unlock();
            }
        }
    }

    private void loadPartners() {
        List<PartnerDTO> partnerList = subscriptionServiceManager.getPartners();
        if (CollectionUtils.isNotEmpty(partnerList) && writeLock.tryLock()) {
            try {
                Map<String, PartnerDTO> partnerMap = partnerList.stream().collect(Collectors.toMap(PartnerDTO::getPackGroup, Function.identity()));
                partners.clear();
                partners.putAll(partnerMap);
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

    public boolean containsPlan(String planId) {
        return plans.containsKey(NumberUtils.toInt(planId));
    }

    public PlanDTO getPlan(String planId) {
        return plans.get(NumberUtils.toInt(planId));
    }

    public OfferDTO getOffer(int offerId) {
        return offers.get(offerId);
    }

    public PartnerDTO getPartner(String packGroup) {
        return partners.get(packGroup);
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
