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
import in.wynk.payment.core.dao.entity.InvoiceDetails;
import in.wynk.payment.core.dao.repository.InvoiceDetailsDao;
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
@Service(value = CacheBeanNameConstants.INVOICE_DETAILS)
public class InvoiceDetailsCachingService implements IEntityCacheService<InvoiceDetails, String> {

    private final ApplicationContext context;
    private final Map<String, IEntityCacheService<InvoiceDetails, String>> delegate = new HashMap<>();

    @PostConstruct
    @AnalyseTransaction(name = "refreshInMemoryCacheInvoiceDetails")
    @Scheduled(fixedDelay = BaseConstants.IN_MEMORY_CACHE_CRON, initialDelay = BaseConstants.IN_MEMORY_CACHE_CRON)
    public void init() {
        if (MapUtils.isEmpty(delegate)) initDelegate();
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("cacheLoadInit", true);
        delegate.forEach((key, value) -> ((InvoiceDetailsClientCaching) value).init());
        AnalyticService.update("cacheLoadCompleted", true);
    }

    private void initDelegate() {
        for (String bean : context.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<InvoiceDetailsDao>() {
        }))) {
            if (bean.equalsIgnoreCase(InvoiceDetails.class.getSimpleName())) continue;
            final InvoiceDetailsClientCaching cache = new InvoiceDetailsClientCaching(context.getBean(bean, InvoiceDetailsDao.class));
            delegate.put(bean, cache);
        }
    }

    @Override
    public InvoiceDetails get(String key) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + InvoiceDetailsDao.class.getName();
        return delegate.get(cacheBean).get(key);
    }

    @Override
    public InvoiceDetails save(InvoiceDetails details) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + InvoiceDetailsDao.class.getName();
        return delegate.get(cacheBean).save(details);
    }

    @Override
    public Collection<InvoiceDetails> getAll() {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + InvoiceDetailsDao.class.getName();
        return delegate.get(cacheBean).getAll();
    }

    @Override
    public boolean containsKey(String key) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + BaseConstants.COLON + InvoiceDetailsDao.class.getName();
        return delegate.get(cacheBean).containsKey(key);
    }

    @Override
    public Collection<InvoiceDetails> getAllByState(State state) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT) + InvoiceDetailsDao.class.getName();
        return delegate.get(cacheBean).getAllByState(state);
    }

    @RequiredArgsConstructor
    private static class InvoiceDetailsClientCaching implements IEntityCacheService<InvoiceDetails, String> {

        private final Map<String, InvoiceDetails> invoiceDetailsMap = new ConcurrentHashMap<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        private final Lock writeLock = lock.writeLock();
        private final InvoiceDetailsDao dao;

        public void init() {
            AnalyticService.update("class", this.getClass().getSimpleName());
            AnalyticService.update("cacheLoadInit", true);
            load();
            AnalyticService.update("cacheLoadCompleted", true);
        }

        private void load() {
            Collection<InvoiceDetails> details = getAllByState(State.ACTIVE);
            if (CollectionUtils.isNotEmpty(details) && writeLock.tryLock()) {
                try {
                    Map<String, InvoiceDetails> temp = details.stream().collect(Collectors.toMap(InvoiceDetails::getId, Function.identity()));
                    invoiceDetailsMap.clear();
                    invoiceDetailsMap.putAll(temp);
                } catch (Throwable th) {
                    log.error(BaseLoggingMarkers.APPLICATION_ERROR, "Exception occurred while refreshing InvoiceDetails cache. Exception: {}", th.getMessage(), th);
                    throw th;
                } finally {
                    writeLock.unlock();
                }
            }
        }

        @Override
        public InvoiceDetails save(InvoiceDetails details) {
            return dao.save(details);
        }

        @Override
        public InvoiceDetails get(String key) {
            return invoiceDetailsMap.get(key);
        }

        @Override
        public Collection<InvoiceDetails> getAll() {
            return invoiceDetailsMap.values();
        }

        @Override
        public boolean containsKey(String key) {
            return invoiceDetailsMap.containsKey(key);
        }

        @Override
        public Collection<InvoiceDetails> getAllByState(State state) {
            return dao.findAllByState(state);
        }

    }
}
