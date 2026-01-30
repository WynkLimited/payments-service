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
import in.wynk.payment.core.dao.entity.GSTStateCodes;
import in.wynk.payment.core.dao.repository.IGSTStateCodeDao;
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
@Service(value = CacheBeanNameConstants.GST_STATE_CODES)
public class GSTStateCodesCachingService implements IEntityCacheService<GSTStateCodes, String> {

    private final ApplicationContext context;
    private final Map<String, IEntityCacheService<GSTStateCodes, String>> delegate = new HashMap<>();

    @PostConstruct
    @AnalyseTransaction(name = "refreshInMemoryCacheGSTStateCodes")
    @Scheduled(fixedDelay = BaseConstants.IN_MEMORY_CACHE_CRON, initialDelay = BaseConstants.IN_MEMORY_CACHE_CRON)
    public void init() {
        if (MapUtils.isEmpty(delegate)) initDelegate();
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("cacheLoadInit", true);
        delegate.forEach((key, value) -> ((GSTStateCodesClientCaching) value).init());
        AnalyticService.update("cacheLoadCompleted", true);
    }

    private void initDelegate() {
        for (String bean : context.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<IGSTStateCodeDao>() {
        }))) {
            if (bean.equalsIgnoreCase(IGSTStateCodeDao.class.getSimpleName())) continue;
            final GSTStateCodesClientCaching cache = new GSTStateCodesClientCaching(context.getBean(bean, IGSTStateCodeDao.class));
            delegate.put(bean, cache);
        }
    }

    @Override
    public GSTStateCodes get(String key) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IGSTStateCodeDao.class.getName();
        return delegate.get(cacheBean).get(key);
    }

    public GSTStateCodes getByISOStateCode(String key) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IGSTStateCodeDao.class.getName();
        return delegate.get(cacheBean).getAll().stream().filter(sc -> sc.getStateCode().equalsIgnoreCase(key)).findAny().orElse(null);
    }

    @Override
    public GSTStateCodes save(GSTStateCodes code) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IGSTStateCodeDao.class.getName();
        return delegate.get(cacheBean).save(code);
    }

    @Override
    public Collection<GSTStateCodes> getAll() {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IGSTStateCodeDao.class.getName();
        return delegate.get(cacheBean).getAll();
    }

    @Override
    public boolean containsKey(String key) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IGSTStateCodeDao.class.getName();
        return delegate.get(cacheBean).containsKey(key);
    }

    public boolean containsByISOStateCode (String key) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + IGSTStateCodeDao.class.getName();
        return delegate.get(cacheBean).getAll().stream().anyMatch(i-> i.getStateCode().equalsIgnoreCase(key));
    }

    @Override
    public Collection<GSTStateCodes> getAllByState(State state) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + IGSTStateCodeDao.class.getName();
        return delegate.get(cacheBean).getAllByState(state);
    }

    @RequiredArgsConstructor
    private static class GSTStateCodesClientCaching implements IEntityCacheService<GSTStateCodes, String> {

        private final Map<String, GSTStateCodes> gstStateCodesMap = new ConcurrentHashMap<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        private final Lock writeLock = lock.writeLock();
        private final IGSTStateCodeDao dao;

        public void init() {
            AnalyticService.update("class", this.getClass().getSimpleName());
            AnalyticService.update("cacheLoadInit", true);
            load();
            AnalyticService.update("cacheLoadCompleted", true);
        }

        private void load() {
            Collection<GSTStateCodes> codes = getAll();
            if (CollectionUtils.isNotEmpty(codes) && writeLock.tryLock()) {
                try {
                    Map<String, GSTStateCodes> temp = codes.stream().collect(Collectors.toMap(GSTStateCodes::getId, Function.identity()));
                    gstStateCodesMap.clear();
                    gstStateCodesMap.putAll(temp);
                } catch (Throwable th) {
                    log.error(BaseLoggingMarkers.APPLICATION_ERROR, "Exception occurred while refreshing GSTStateCodes cache. Exception: {}", th.getMessage(), th);
                    throw th;
                } finally {
                    writeLock.unlock();
                }
            }
        }

        @Override
        public GSTStateCodes save(GSTStateCodes code) {
            return dao.save(code);
        }

        @Override
        public GSTStateCodes get(String key) {
            return gstStateCodesMap.get(key);
        }

        @Override
        public Collection<GSTStateCodes> getAll() {
            return dao.findAll();
        }

        @Override
        public boolean containsKey(String key) {
            return gstStateCodesMap.containsKey(key);
        }

        @Override
        public Collection<GSTStateCodes> getAllByState(State state) {
            return dao.findAllByState(state);
        }

    }

}