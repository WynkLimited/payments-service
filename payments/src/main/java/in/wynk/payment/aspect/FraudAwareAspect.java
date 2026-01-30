package in.wynk.payment.aspect;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.validations.IBaseRequest;
import in.wynk.common.validations.IHandler;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.FraudAware;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * @author Nishesh Pandey
 */
@Aspect
public class FraudAwareAspect {

    @Before(value = "execution(@in.wynk.payment.aspect.advice.FraudAware * *.*(..))")
    public void beforeFraudAware(JoinPoint joinPoint) throws InterruptedException {
        final Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        final FraudAware fraudAware = method.getAnnotation(FraudAware.class);
        final Object[] args = joinPoint.getArgs();
        if (args.length == 0)
            throw new WynkRuntimeException("You must supply fraud chain name");
        BeanLocatorFactory.getBean(fraudAware.name(), IHandler.class).handle((IBaseRequest) args[args.length - 1]);
    }

}
