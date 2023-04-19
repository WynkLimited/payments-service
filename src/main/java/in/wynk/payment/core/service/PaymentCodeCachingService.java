package in.wynk.payment.core.service;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.data.enums.State;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.dao.repository.IPaymentCodeDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
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

import static in.wynk.common.constant.BaseConstants.COLON;
import static in.wynk.common.constant.BaseConstants.IN_MEMORY_CACHE_CRON;
import static in.wynk.common.constant.CacheBeanNameConstants.PAYMENT_CODE;
import static in.wynk.logging.BaseLoggingMarkers.APPLICATION_ERROR;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Slf4j
@RequiredArgsConstructor
@Service(value = PAYMENT_CODE)
public class PaymentCodeCachingService implements IEntityCacheService<PaymentGateway, String> {

    private final ApplicationContext context;
    private final Map<String, IEntityCacheService<PaymentGateway, String>> delegate = new HashMap<>();

    public static PaymentGateway getFromCode(String codeStr) {
        for (PaymentGateway paymentGateway : (BeanLocatorFactory.getBean(PaymentCodeCachingService.class)).getAll())
            if (StringUtils.equalsIgnoreCase(codeStr, paymentGateway.getCode())) return paymentGateway;
        throw new WynkRuntimeException(PaymentErrorType.PAY001);
    }

    public static PaymentGateway getFromPaymentCode(String paymentCode) {
        for (PaymentGateway code : (BeanLocatorFactory.getBean(PaymentCodeCachingService.class)).getAll())
            if (StringUtils.equalsIgnoreCase(paymentCode, code.getId())) return code;
        throw new WynkRuntimeException(PaymentErrorType.PAY001);
    }

    @PostConstruct
    @AnalyseTransaction(name = "refreshInMemoryCachePaymentCode")
    @Scheduled(fixedDelay = IN_MEMORY_CACHE_CRON, initialDelay = IN_MEMORY_CACHE_CRON)
    public void init() {
        if (MapUtils.isEmpty(delegate)) initDelegate();
        AnalyticService.update("class", this.getClass().getSimpleName());
        AnalyticService.update("cacheLoadInit", true);
        delegate.forEach((key, value) -> ((PaymentCodeClientCaching) value).init());
        AnalyticService.update("cacheLoadCompleted", true);
    }

    @Override
    public PaymentGateway get(String key) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT) + COLON + IPaymentCodeDao.class.getName();
        return delegate.get(cacheBean).get(key);
    }

    @Override
    public PaymentGateway save(PaymentGateway item) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT) + COLON + IPaymentCodeDao.class.getName();
        return delegate.get(cacheBean).save(item);
    }

    @Override
    public Collection<PaymentGateway> getAll() {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT) + COLON + IPaymentCodeDao.class.getName();
        return delegate.get(cacheBean).getAll();
    }

    @Override
    public boolean containsKey(String key) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT) + COLON + IPaymentCodeDao.class.getName();
        return delegate.get(cacheBean).containsKey(key);
    }

    @Override
    public Collection<PaymentGateway> getAllByState(State state) {
        final String cacheBean = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT) + COLON + IPaymentCodeDao.class.getName();
        return delegate.get(cacheBean).getAllByState(state);
    }

    private void initDelegate() {
        for (String bean : context.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<IPaymentCodeDao>() {
        }))) {
            if (bean.equalsIgnoreCase(IPaymentCodeDao.class.getSimpleName())) continue;
            final PaymentCodeClientCaching cache = new PaymentCodeClientCaching(context.getBean(bean, IPaymentCodeDao.class));
            delegate.put(bean, cache);
        }
    }

    @RequiredArgsConstructor
    private static class PaymentCodeClientCaching implements IEntityCacheService<PaymentGateway, String> {

        private final Map<String, PaymentGateway> paymentCodeMap = new ConcurrentHashMap<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        private final Lock writeLock = lock.writeLock();
        private final IPaymentCodeDao dao;

        public void init() {
            AnalyticService.update("class", this.getClass().getSimpleName());
            AnalyticService.update("cacheLoadInit", true);
            load();
            AnalyticService.update("cacheLoadCompleted", true);
        }

        private void load() {
            Collection<PaymentGateway> paymentGatewayCollection = this.getAllByState(State.ACTIVE);
            if (CollectionUtils.isNotEmpty(paymentGatewayCollection) && writeLock.tryLock()) {
                try {
                    Map<String, PaymentGateway> temp = paymentGatewayCollection.stream().collect(Collectors.toMap(PaymentGateway::getId, Function.identity()));
                    paymentCodeMap.clear();
                    paymentCodeMap.putAll(temp);
                } catch (Throwable th) {
                    log.error(APPLICATION_ERROR, "Exception occurred while refreshing payment code config cache. Exception: {}", th.getMessage(), th);
                    throw th;
                } finally {
                    writeLock.unlock();
                }
            }
        }

        @Override
        public PaymentGateway get(String key) {
            return paymentCodeMap.get(key);
        }

        @Override
        public PaymentGateway save(PaymentGateway item) {
            return dao.save(item);
        }

        @Override
        public Collection<PaymentGateway> getAll() {
            return paymentCodeMap.values();
        }

        @Override
        public boolean containsKey(String key) {
            return paymentCodeMap.containsKey(key);
        }

        @Override
        public Collection<PaymentGateway> getAllByState(State state) {
            return dao.findAllByState(state);
        }
    }

}