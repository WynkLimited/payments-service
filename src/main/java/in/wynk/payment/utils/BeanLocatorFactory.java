package in.wynk.payment.utils;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component
public class BeanLocatorFactory implements ApplicationListener<ContextRefreshedEvent> {
    private static ApplicationContext applicationContext;

    public static <T> T getBean(final String name, final Class<T> clazz) {
        try {
            return applicationContext.getBean(name, clazz);
        } catch (BeansException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY005);
        }
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        BeanLocatorFactory.applicationContext = event.getApplicationContext();
    }
}
