package in.wynk.payment.aspect;

import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.dto.SessionDTO;
import in.wynk.payment.aspect.advice.ClientAwareWrapper;
import in.wynk.session.context.SessionContextHolder;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;

import static in.wynk.common.constant.BaseConstants.CLIENT;

@Aspect
public class ClientAwareWrapperAspect {

    @Before("execution(@in.wynk.payment.aspect.advice.ClientAwareWrapper * *.*(..))")
    public void beforeClientAwareWrapper(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        ClientAwareWrapper clientAwareWrapper = method.getAnnotation(ClientAwareWrapper.class);
        if (clientAwareWrapper.isS2S())
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