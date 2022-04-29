package in.wynk.payment.core.service;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.constant.CacheBeanNameConstants;
import in.wynk.common.dto.ICacheService;
import in.wynk.data.enums.State;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.repository.IPaymentGroupDao;
import in.wynk.payment.core.dao.repository.IPaymentMethodDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Slf4j
@Service(CacheBeanNameConstants.GROUPED_PAYMENT_METHOD)
@RequiredArgsConstructor
public class GroupedPaymentMethodCachingService implements ICacheService<List<PaymentMethod>, String> {

    private final ApplicationContext context;
    private final Map<String, ICacheService<List<PaymentMethod>, String>> delegate = new ConcurrentHashMap<>();

    @PostConstruct
    @AnalyseTransaction(name = "refreshInMemoryCacheGroupedPaymentMethod")
    @Scheduled(fixedDelay = BaseConstants.IN_MEMORY_CACHE_CRON, initialDelay = BaseConstants.IN_MEMORY_CACHE_CRON)
    public void load() {
        for (String bean : context.getBeanNamesForType(ResolvableType.forType(new ParameterizedTypeReference<IPaymentGroupDao>() {
        }))) {
            if (bean.equalsIgnoreCase(IPaymentGroupDao.class.getSimpleName())) continue;
            final String client = bean.replace(IPaymentGroupDao.class.getName(), "").replace(BaseConstants.COLON, "");
            if (!delegate.containsKey(client)) delegate.put(client, new GroupedPaymentMethodClientCaching());
            ((GroupedPaymentMethodClientCaching) delegate.get(client)).load(client);
        }
    }

    @Override
    public List<PaymentMethod> get(String key) {
        final String client = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT);
        return delegate.get(client).get(key);
    }

    @Override
    public List<PaymentMethod> save(List<PaymentMethod> item) {
        throw new WynkRuntimeException("Method is not implemented!");
    }

    @Override
    public Collection<List<PaymentMethod>> getAll() {
        final String client = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT);
        return delegate.get(client).getAll();
    }

    public Map<String, List<PaymentMethod>> getGroupPaymentMethods() {
        final String client = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT);
        return ((GroupedPaymentMethodClientCaching) delegate.get(client)).getGroupPaymentMethods();
    }

    @Override
    public boolean containsKey(String key) {
        final String client = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT);
        return delegate.get(client).containsKey(key);
    }

    private class GroupedPaymentMethodClientCaching implements ICacheService<List<PaymentMethod>, String> {

        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        private final Lock writeLock = lock.writeLock();
        private final Map<String, List<PaymentMethod>> groupedPaymentMethods = new ConcurrentHashMap<>();

        private void load(String client) {
            final String groupBean = client + BaseConstants.COLON + IPaymentGroupDao.class.getName();
            List<PaymentGroup> activeGroups = context.getBean(groupBean, IPaymentGroupDao.class).findAllByState(State.ACTIVE);
            if (CollectionUtils.isNotEmpty(activeGroups) && writeLock.tryLock()) {
                try {
                    for (PaymentGroup group : activeGroups) {
                        final List<PaymentMethod> activeGroupMethods = context.getBean(client + BaseConstants.COLON + IPaymentMethodDao.class.getName(), IPaymentMethodDao.class).findAllByState(State.ACTIVE).stream().filter(method -> method.getGroup().equalsIgnoreCase(group.getId())).collect(Collectors.toList());
                        groupedPaymentMethods.put(group.getId(), activeGroupMethods);
                    }
                } catch (Throwable th) {
                    log.error(BaseLoggingMarkers.APPLICATION_ERROR, "Exception occurred while refreshing paymentGroup cache. Exception: {}", th.getMessage(), th);
                    throw th;
                } finally {
                    writeLock.unlock();
                }
            }
        }


        @Override
        public List<PaymentMethod> get(String key) {
            return groupedPaymentMethods.get(key);
        }

        @Override
        public List<PaymentMethod> save(List<PaymentMethod> item) {
            throw new WynkRuntimeException("Method is not implemented!");
        }

        @Override
        public Collection<List<PaymentMethod>> getAll() {
            return groupedPaymentMethods.values();
        }

        @Override
        public boolean containsKey(String key) {
            return groupedPaymentMethods.containsKey(key);
        }

        public Map<String, List<PaymentMethod>> getGroupPaymentMethods() {
            return groupedPaymentMethods;
        }
    }

}
