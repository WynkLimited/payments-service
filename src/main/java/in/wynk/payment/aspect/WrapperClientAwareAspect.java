package in.wynk.payment.aspect;

import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.dto.SessionDTO;
import in.wynk.payment.aspect.advice.WrapperClientAware;
import in.wynk.session.context.SessionContextHolder;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;

import static in.wynk.common.constant.BaseConstants.CLIENT;

@Aspect
// WARNING:
// Don't change its name cause Annotations' aspect are evaluated in Alphabetical order in Spring and for this to work we need Session to be loaded first.
// Emphasizing that ManageSessionAspect should get executed first and hence WrapperClientAwareAspect Alphabetically will come after that.
public class WrapperClientAwareAspect {

    @Before("execution(@in.wynk.payment.aspect.advice.WrapperClientAware * *.*(..))")
    public void beforeClientAwareWrapper(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        WrapperClientAware wrapperClientAware = method.getAnnotation(WrapperClientAware.class);
        if (wrapperClientAware.isS2S())
            loadClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        else loadClientByAlias(SessionContextHolder.<SessionDTO>getBody().get(CLIENT));
    }

    @ClientAware(clientId = "#clientId")
    private void loadClientById(String clientId) {
    }

    @ClientAware(clientAlias = "#clientAlias")
    private void loadClientByAlias(String clientAlias) {
    }

}