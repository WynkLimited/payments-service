package in.wynk.payment.core.service;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.constant.CacheBeanNameConstants;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.data.enums.State;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import in.wynk.payment.core.dao.repository.IPaymentGroupDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service(value = CacheBeanNameConstants.PAYMENT_GROUP)
public class PaymentGroupCachingService implements IEntityCacheService<PaymentGroup, String> {

    private final ApplicationContext context;
    private final Map<String, IEntityCacheService<PaymentGroup, String>> delegate = new HashMap<>();

    @PostConstruct
    @AnalyseTransaction(name = "refreshInMemoryCachePaymentGroup")
    @Scheduled(fixedDelay = BaseConstants.IN_MEMORY_CACHE_CRON, initialDelay = BaseConstants.IN_MEMORY_CACHE_CRON)
    public void init() {
        if (MapUtils.isEmpty(delegate)) initDelegate();
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("cacheLoadInit", true);
        delegate.forEach((key, value) -> ((PaymentGroupClientCaching) value).init());
        AnalyticService.update("cacheLoadCompleted", true);
    }

    private void initDelegate() {
        for (String bean : context.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<IPaymentGroupDao>() {
        }))) {
            if (bean.equalsIgnoreCase(IPaymentGroupDao.class.getSimpleName())) continue;
            final PaymentGroupClientCaching cache = new PaymentGroupClientCaching(context.getBean(bean, IPaymentGroupDao.class));
            delegate.put(bean, cache);
        }
    }

    @Override
    public PaymentGroup get(String key) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IPaymentGroupDao.class.getName();
        return delegate.get(cacheBean).get(key);
    }

    @Override
    public PaymentGroup save(PaymentGroup method) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IPaymentGroupDao.class.getName();
        return delegate.get(cacheBean).save(method);
    }

    @Override
    public Collection<PaymentGroup> getAll() {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IPaymentGroupDao.class.getName();
        return delegate.get(cacheBean).getAll();
    }

    @Override
    public boolean containsKey(String key) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IPaymentGroupDao.class.getName();
        return delegate.get(cacheBean).containsKey(key);
    }

    @Override
    public Collection<PaymentGroup> getAllByState(State state) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IPaymentGroupDao.class.getName();
        return delegate.get(cacheBean).getAllByState(state);
    }

    @RequiredArgsConstructor
    private static class PaymentGroupClientCaching implements IEntityCacheService<PaymentGroup, String> {

        private final Map<String, PaymentGroup> paymentGroupMap = new ConcurrentHashMap<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        private final Lock writeLock = lock.writeLock();
        private final IPaymentGroupDao dao;

        public void init() {
            AnalyticService.update("class", this.getClass().getSimpleName());
            AnalyticService.update("cacheLoadInit", true);
            load();
            AnalyticService.update("cacheLoadCompleted", true);
        }

        private void load() {
            Collection<PaymentGroup> allMethods = getAllByState(State.ACTIVE);
            if (CollectionUtils.isNotEmpty(allMethods) && writeLock.tryLock()) {
                try {
                    Map<String, PaymentGroup> temp = allMethods.stream().collect(Collectors.toMap(PaymentGroup::getId, Function.identity()));
                    paymentGroupMap.clear();
                    paymentGroupMap.putAll(temp);
                } catch (Throwable th) {
                    log.error(BaseLoggingMarkers.APPLICATION_ERROR, "Exception occurred while refreshing paymentGroup cache. Exception: {}", th.getMessage(), th);
                    throw th;
                } finally {
                    writeLock.unlock();
                }
            }
        }

        @Override
        public PaymentGroup save(PaymentGroup paymentGroup) {
            return dao.save(paymentGroup);
        }

        @Override
        public PaymentGroup get(String key) {
            return paymentGroupMap.get(key);
        }

        @Override
        public Collection<PaymentGroup> getAll() {
            return paymentGroupMap.values();
        }

        @Override
        public boolean containsKey(String key) {
            return paymentGroupMap.containsKey(key);
        }

        @Override
        public Collection<PaymentGroup> getAllByState(State state) {
            return dao.findAllByState(state);
        }

    }

}