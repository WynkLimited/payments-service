package in.wynk.payment.service;

import in.wynk.commons.enums.PaymentGroup;
import in.wynk.commons.enums.State;
import in.wynk.payment.core.entity.PaymentMethod;
import in.wynk.payment.dao.PaymentMethodDao;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static in.wynk.logging.BaseLoggingMarkers.APPLICATION_ERROR;

@Service
@Getter
public class PaymentCachingService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCachingService.class);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    @Autowired
    private PaymentMethodDao paymentMethodDao;
    private Map<PaymentGroup, List<PaymentMethod>> groupedPaymentMethods = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadPayments();
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


    private List<PaymentMethod> getActivePaymentMethods() {
        return paymentMethodDao.findAllByState(State.ACTIVE);
    }


}
