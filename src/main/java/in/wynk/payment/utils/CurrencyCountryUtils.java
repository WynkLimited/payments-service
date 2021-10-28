package in.wynk.payment.utils;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.country.core.service.CountryCurrencyDetailsCachingService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.PlanDTO;
import org.apache.commons.lang.StringUtils;

import java.util.Objects;


public class CurrencyCountryUtils {

    public static String findCountryCodeByCurrency(String currency) {
        CountryCurrencyDetailsCachingService countryCurrencyDetailsCachingService = BeanLocatorFactory.getBean(CountryCurrencyDetailsCachingService.class);
        if (countryCurrencyDetailsCachingService.containsCurrencyKey(currency)) {
            return countryCurrencyDetailsCachingService.getByCurrency(currency).getCountryCode();
        } else {
            return null;
        }
    }

    public static String findCountryCodeByPlanId(int planId) {
        PaymentCachingService paymentCachingService = BeanLocatorFactory.getBean(PaymentCachingService.class);
        PlanDTO plan = paymentCachingService.getPlan(planId);
        if (Objects.isNull(plan) || Objects.isNull(plan.getPrice()) || StringUtils.isEmpty(plan.getPrice().getCurrency())) {
            return null;
        } else {
            return findCountryCodeByCurrency(plan.getPrice().getCurrency());
        }
    }
}
