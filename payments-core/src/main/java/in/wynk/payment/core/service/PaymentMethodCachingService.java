package in.wynk.payment.core.service;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.constant.BaseConstants;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.data.enums.State;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.repository.IPaymentMethodDao;
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

import static in.wynk.common.constant.BaseConstants.IN_MEMORY_CACHE_CRON;
import static in.wynk.common.constant.CacheBeanNameConstants.PAYMENT_METHOD;
import static in.wynk.logging.BaseLoggingMarkers.APPLICATION_ERROR;

@Slf4j
@RequiredArgsConstructor
@Service(value = PAYMENT_METHOD)
public class PaymentMethodCachingService implements IEntityCacheService<PaymentMethod, String> {

    private final ApplicationContext context;
    private final Map<String, IEntityCacheService<PaymentMethod, String>> delegate = new HashMap<>();

    @PostConstruct
    @AnalyseTransaction(name = "refreshInMemoryCachePaymentMethod")
    @Scheduled(fixedDelay = IN_MEMORY_CACHE_CRON, initialDelay = IN_MEMORY_CACHE_CRON)
    public void init() {
        if (MapUtils.isEmpty(delegate)) initDelegate();
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("cacheLoadInit", true);
        delegate.forEach((key, value) -> ((PaymentMethodClientCaching) value).init());
        AnalyticService.update("cacheLoadCompleted", true);
    }

    private void initDelegate() {
        for (String bean : context.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<IPaymentMethodDao>() {
        }))) {
            if (bean.equalsIgnoreCase(IPaymentMethodDao.class.getSimpleName())) continue;
            final PaymentMethodClientCaching cache = new PaymentMethodClientCaching(context.getBean(bean, IPaymentMethodDao.class));
            delegate.put(bean, cache);
        }
    }

    @Override
    public PaymentMethod get(String key) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IPaymentMethodDao.class.getName();
        return delegate.get(cacheBean).get(key);
    }

    public PaymentMethod getByAlias(String alias) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IPaymentMethodDao.class.getName();
        return ((PaymentMethodClientCaching) delegate.get(cacheBean)).getByAlias(alias);
    }

    public boolean containsByAlias(String alias) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IPaymentMethodDao.class.getName();
        return ((PaymentMethodClientCaching) delegate.get(cacheBean)).containsByAlias(alias);
    }


    @Override
    public PaymentMethod save(PaymentMethod method) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IPaymentMethodDao.class.getName();
        return delegate.get(cacheBean).save(method);
    }

    @Override
    public Collection<PaymentMethod> getAll() {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IPaymentMethodDao.class.getName();
        return delegate.get(cacheBean).getAll();
    }

    @Override
    public boolean containsKey(String key) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IPaymentMethodDao.class.getName();
        return delegate.get(cacheBean).containsKey(key);
    }

    @Override
    public Collection<PaymentMethod> getAllByState(State state) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IPaymentMethodDao.class.getName();
        return delegate.get(cacheBean).getAllByState(state);
    }

    @RequiredArgsConstructor
    private static class PaymentMethodClientCaching  implements IEntityCacheService<PaymentMethod, String> {

        private final Map<String, PaymentMethod> paymentMethodMap = new ConcurrentHashMap<>();
        private final Map<String, String> aliasToIdMap = new ConcurrentHashMap<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        private final Lock writeLock = lock.writeLock();
        private final IPaymentMethodDao dao;

        public void init() {
            AnalyticService.update("class", this.getClass().getSimpleName());
            AnalyticService.update("cacheLoadInit", true);
            load();
            AnalyticService.update("cacheLoadCompleted", true);
        }

        private void load() {
            Collection<PaymentMethod> allMethods = getAllByState(State.ACTIVE);
            if (CollectionUtils.isNotEmpty(allMethods) && writeLock.tryLock()) {
                try {
                    Map<String, PaymentMethod> temp = allMethods.stream().collect(Collectors.toMap(PaymentMethod::getId, Function.identity()));
                    Map<String, String> aliasToIdsLocal =temp.values().stream().collect(Collectors.toMap(PaymentMethod::getAlias, PaymentMethod::getId, (k1, k2) -> k1));
                    aliasToIdMap.clear();
                    paymentMethodMap.clear();
                    paymentMethodMap.putAll(temp);
                    aliasToIdMap.putAll(aliasToIdsLocal);
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

        public PaymentMethod getByAlias(String alias) {
            final String id = aliasToIdMap.getOrDefault(alias, alias);
            return get(id);
        }

        public boolean containsByAlias(String alias) {
            final String id = aliasToIdMap.getOrDefault(alias, alias);
            return containsKey(id);
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

}