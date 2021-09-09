package in.wynk.payment.utils;

import in.wynk.common.utils.EmbeddedPropertyResolver;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_CLIENT_PLACE_HOLDER;

public class PropertyResolverUtils {
    public static String resolve(String client, String paymentCode, String field) {
        return EmbeddedPropertyResolver.resolveEmbeddedValue(PAYMENT_CLIENT_PLACE_HOLDER.replace("%c",client).replace("%p",paymentCode).replace("%f",field));
    }
}
