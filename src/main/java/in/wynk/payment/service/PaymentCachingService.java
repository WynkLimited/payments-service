package in.wynk.payment.service;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.service.GroupedPaymentMethodCachingService;
import in.wynk.payment.core.service.PaymentGroupCachingService;
import in.wynk.payment.core.service.SkuToSkuCachingService;
import in.wynk.subscription.common.dto.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collection;
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

@Slf4j
@Getter
@Service
@RequiredArgsConstructor
public class PaymentCachingService {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    private final Map<Integer, OfferDTO> offers = new ConcurrentHashMap<>();
    private final Map<String, PlanDTO> skuToPlan = new ConcurrentHashMap<>();
    private final Map<String, PartnerDTO> partners = new ConcurrentHashMap<>();
    private final Map<String, List<PaymentMethod>> groupedPaymentMethods = new ConcurrentHashMap<>();

    private final PlanDtoCachingService planDtoCachingService;
    private final ItemDtoCachingService itemDtoCachingService;
    private final ISubscriptionServiceManager subscriptionServiceManager;

    @PostConstruct
    @AnalyseTransaction(name = "refreshInMemoryCache")
    @Scheduled(fixedDelay = 30 * 60 * 1000L, initialDelay = 30 * 60 * 1000L)
    public void init() {
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("cacheLoadInit", true);
        loadProducts();
        loadPlans();
        loadOffers();
        loadPartners();
        AnalyticService.update("cacheLoadCompleted", true);
    }

    private void loadProducts() {
        List<ProductDTO> products = subscriptionServiceManager.getProducts();
    }


    private void loadPlans() {
        Collection<PlanDTO> planList = planDtoCachingService.getAll();
        if (CollectionUtils.isNotEmpty(planList) && writeLock.tryLock()) {
            try {
                Map<String, PlanDTO> skuToPlanMap = new HashMap<>();
                for (PlanDTO planDTO : planList) {
                    if (MapUtils.isNotEmpty(planDTO.getSku())) {
                        for (String sku : planDTO.getSku().values()) {
                            skuToPlanMap.putIfAbsent(sku, planDTO);
                        }
                    }
                }
                skuToPlan.clear();
                skuToPlan.putAll(skuToPlanMap);
            } catch (Throwable th) {
                log.error(APPLICATION_ERROR, "Exception occurred while refreshing plans config cache. Exception: {}", th.getMessage(), th);
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
                log.error(APPLICATION_ERROR, "Exception occurred while refreshing offer config cache. Exception: {}", th.getMessage(), th);
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
                log.error(APPLICATION_ERROR, "Exception occurred while refreshing partner config cache. Exception: {}", th.getMessage(), th);
                throw th;
            } finally {
                writeLock.unlock();
            }
        }
    }

    public ItemDTO getItem(String itemId) {
        return itemDtoCachingService.get(itemId);
    }

    public PlanDTO getPlan(int planId) {
        return planDtoCachingService.get(planId);
    }

    public boolean containsPlan(String planId) {
        return planDtoCachingService.containsKey(NumberUtils.toInt(planId));
    }

    public boolean containsItem(String itemId) {
        return itemDtoCachingService.containsKey(itemId);
    }

    public PlanDTO getPlan(String planId) {
        return planDtoCachingService.get(NumberUtils.toInt(planId));
    }

    public OfferDTO getOffer(int offerId) {
        return offers.get(offerId);
    }

    public PartnerDTO getPartner(String packGroup) {
        return partners.get(packGroup);
    }

    public String getNewSku(String oldSku) {
        if (containsSku(oldSku)) return BeanLocatorFactory.getBean(SkuToSkuCachingService.class).get(oldSku).getNewSku();
        return null;
    }

    public boolean containsSku(String oldSku) {
        return BeanLocatorFactory.getBean(SkuToSkuCachingService.class).containsKey(oldSku);
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

    public Map<String, PaymentGroup> getPaymentGroups() {
        return BeanLocatorFactory.getBean(PaymentGroupCachingService.class).getAll().stream().collect(Collectors.toMap(PaymentGroup::getId, Function.identity()));
    }

    public Map<String, List<PaymentMethod>> getGroupedPaymentMethods() {
        return BeanLocatorFactory.getBean(GroupedPaymentMethodCachingService.class).getGroupPaymentMethods();
    }

}