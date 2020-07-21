package in.wynk.payment.core.dto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WynkQueue {
    String queueName() default "";
    String delaySeconds() default "";
    String queueType() default "STANDARD";
}