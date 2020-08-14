package in.wynk.payment.aspect;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.TransactionContext;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.service.ITransactionManagerService;
import java.lang.reflect.Method;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;

/**
 * @author Abhishek
 * @created 13/08/20
 */
@Aspect
public class TransactionAwareAspect {

    @Autowired
    private ITransactionManagerService transactionManager;

    @Before(value = "execution(@in.wynk.payment.aspect.advice.TransactionAware * *.*(..))")
    public void beforeTransactionAware(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        TransactionAware transactionAware = method.getAnnotation(TransactionAware.class);
        if (!StringUtils.isEmpty(transactionAware.txnId())) {
            String txnId = parseTxnId(transactionAware.txnId());
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

    private String parseTxnId(String expressionToEval) {
        ExpressionParser expressionParser = new SpelExpressionParser();
        Expression expression = expressionParser.parseExpression(expressionToEval);
        return expression.getValue(String.class);
    }

}
