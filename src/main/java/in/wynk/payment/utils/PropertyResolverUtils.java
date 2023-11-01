package in.wynk.payment.utils;

import in.wynk.common.utils.EmbeddedPropertyResolver;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_CLIENT_PLACE_HOLDER;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_CLIENT_PLACE_HOLDER2;

public class PropertyResolverUtils {
    public static String resolve(String client, String paymentCode, String field) {
        return EmbeddedPropertyResolver.resolveEmbeddedValue("${"+PAYMENT_CLIENT_PLACE_HOLDER.replace("%c",client).replace("%p",paymentCode).replace("%f",field)+"}");
    }

    public static String resolve(String client, String baseRoute, String paymentCode, String field) {
        return EmbeddedPropertyResolver.resolveEmbeddedValue("${"+PAYMENT_CLIENT_PLACE_HOLDER2.replace("%c",client).replace("%p",paymentCode).replace("%r",baseRoute).replace("%f",field)+"}");
    }
}
