package in.wynk.payment.aspect;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
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

/**
 * @author Abhishek
 * @created 13/08/20
 */
@Aspect
public class TransactionAwareAspect {

    @Autowired
    private IRuleEvaluator ruleEvaluator;

    @Autowired
    private ITransactionManagerService transactionManager;

    @Before(value = "execution(@in.wynk.payment.aspect.advice.TransactionAware * *.*(..))")
    public void beforeTransactionAware(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        TransactionAware transactionAware = method.getAnnotation(TransactionAware.class);
        if (!StringUtils.isEmpty(transactionAware.txnId())) {
            String txnId = parseSpel(joinPoint, transactionAware);
            final Transaction transaction = transactionManager.get(txnId);
            if(transaction != null){
                TransactionContext.set(transaction);
            } else{
                throw new WynkRuntimeException("Transaction is null");
            }
        } else{
            throw new WynkRuntimeException("Empty txn id");
        }
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
