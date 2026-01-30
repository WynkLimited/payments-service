package in.wynk.payment.aspect;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.lock.WynkRedisLockService;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.TransactionDetails;
import in.wynk.payment.service.IPurchaseDetailsManger;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.spel.IRuleEvaluator;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static in.wynk.payment.core.constant.PaymentErrorType.PAY010;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY401;

@Aspect
public class TransactionAwareAspect {

    @Autowired
    private IRuleEvaluator ruleEvaluator;

    @Autowired
    private WynkRedisLockService wynkRedisLockService;

    @Autowired
    private IPurchaseDetailsManger payerDetailsManager;

    @Autowired
    private ITransactionManagerService transactionManager;

    @Before(value = "execution(@in.wynk.payment.aspect.advice.TransactionAware * *.*(..))")
    public void beforeTransactionAware(JoinPoint joinPoint) throws InterruptedException {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        TransactionAware transactionAware = method.getAnnotation(TransactionAware.class);
        if (!StringUtils.isEmpty(transactionAware.txnId())) {
            String txnId = parseSpel(joinPoint, transactionAware);
            if (transactionAware.lock()) {
                Lock lock = wynkRedisLockService.getWynkRedisLock(txnId);
                if (lock.tryLock(2500, TimeUnit.MILLISECONDS)) {
                    try {
                        processTxnId(txnId);
                    } catch (WynkRuntimeException e) {
                        throw e;
                    } finally {
                        lock.unlock();
                    }
                } else {
                    throw new WynkRuntimeException(PAY401);
                }
            } else {
                processTxnId(txnId);
            }
        } else {
            throw new WynkRuntimeException(PAY010);
        }
    }

    private void processTxnId(String txnId) {
        final Transaction transaction = transactionManager.get(txnId);
        final TransactionDetails.TransactionDetailsBuilder transactionDetailsBuilder = TransactionDetails.builder().transaction(transaction);
        Optional.ofNullable(payerDetailsManager.get(transaction)).ifPresent(transactionDetailsBuilder::purchaseDetails);
        TransactionContext.set(transactionDetailsBuilder.build());
    }

    private String parseSpel(JoinPoint joinPoint, TransactionAware transactionAware) {
        CodeSignature methodSignature = (CodeSignature) joinPoint.getSignature();
        String[] keyParams = methodSignature.getParameterNames();
        Object[] valueParams = joinPoint.getArgs();
        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < Objects.requireNonNull(keyParams).length; i++) {
            context.setVariable(keyParams[i], valueParams[i]);
        }
        try {
            return ruleEvaluator.evaluate(transactionAware.txnId(), () -> context, String.class);
        } catch (Exception e) {
            throw new WynkRuntimeException("Unable to parse transactionId " + transactionAware.txnId() + " due to", e);
        }
    }

}