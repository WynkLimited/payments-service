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
import in.wynk.payment.core.dao.entity.SkuMapping;
import in.wynk.payment.core.dao.repository.SkuDao;
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
@Service(value = CacheBeanNameConstants.SKU_TO_SKU)
public class SkuToSkuCachingService implements IEntityCacheService<SkuMapping, String> {

    private final ApplicationContext context;
    private final Map<String, IEntityCacheService<SkuMapping, String>> delegate = new HashMap<>();

    @PostConstruct
    @AnalyseTransaction(name = "refreshInMemoryCacheSkuMapping")
    @Scheduled(fixedDelay = BaseConstants.IN_MEMORY_CACHE_CRON, initialDelay = BaseConstants.IN_MEMORY_CACHE_CRON)
    public void init() {
        if (MapUtils.isEmpty(delegate)) initDelegate();
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("cacheLoadInit", true);
        delegate.forEach((key, value) -> ((SkuMappingClientCaching) value).init());
        AnalyticService.update("cacheLoadCompleted", true);
    }

    private void initDelegate() {
        for (String bean : context.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<SkuDao>() {
        }))) {
            if (bean.equalsIgnoreCase(SkuDao.class.getSimpleName())) continue;
            final SkuMappingClientCaching cache = new SkuMappingClientCaching(context.getBean(bean, SkuDao.class));
            delegate.put(bean, cache);
        }
    }

    @Override
    public SkuMapping get(String key) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + SkuDao.class.getName();
        return delegate.get(cacheBean).get(key);
    }

    @Override
    public SkuMapping save(SkuMapping method) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + SkuDao.class.getName();
        return delegate.get(cacheBean).save(method);
    }

    @Override
    public Collection<SkuMapping> getAll() {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + SkuDao.class.getName();
        return delegate.get(cacheBean).getAll();
    }

    @Override
    public boolean containsKey(String key) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + SkuDao.class.getName();
        return delegate.get(cacheBean).containsKey(key);
    }

    @Override
    public Collection<SkuMapping> getAllByState(State state) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + SkuDao.class.getName();
        return delegate.get(cacheBean).getAllByState(state);
    }

    @RequiredArgsConstructor
    private static class SkuMappingClientCaching implements IEntityCacheService<SkuMapping, String> {

        private final Map<String, SkuMapping> skuMappingMap = new ConcurrentHashMap<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        private final Lock writeLock = lock.writeLock();
        private final SkuDao dao;

        public void init() {
            AnalyticService.update("class", this.getClass().getSimpleName());
            AnalyticService.update("cacheLoadInit", true);
            load();
            AnalyticService.update("cacheLoadCompleted", true);
        }

        private void load() {
            Collection<SkuMapping> skuMappings = getAllByState(State.ACTIVE);
            if (CollectionUtils.isNotEmpty(skuMappings) && writeLock.tryLock()) {
                try {
                    Map<String, SkuMapping> temp = skuMappings.stream().collect(Collectors.toMap(SkuMapping::getOldSku, Function.identity()));
                    skuMappingMap.clear();
                    skuMappingMap.putAll(temp);
                } catch (Throwable th) {
                    log.error(BaseLoggingMarkers.APPLICATION_ERROR, "Exception occurred while refreshing SkuMapping cache. Exception: {}", th.getMessage(), th);
                    throw th;
                } finally {
                    writeLock.unlock();
                }
            }
        }

        @Override
        public SkuMapping save(SkuMapping skuMapping) {
            return dao.save(skuMapping);
        }

        @Override
        public SkuMapping get(String key) {
            return skuMappingMap.get(key);
        }

        @Override
        public Collection<SkuMapping> getAll() {
            return skuMappingMap.values();
        }

        @Override
        public boolean containsKey(String key) {
            return skuMappingMap.containsKey(key);
        }

        @Override
        public Collection<SkuMapping> getAllByState(State state) {
            return dao.findAllByState(state);
        }

    }

}