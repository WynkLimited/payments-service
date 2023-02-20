package in.wynk.payment.utils;

import in.wynk.payment.common.enums.BillingCycle;
import in.wynk.payment.common.utils.BillingUtils;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.Calendar;
import java.util.Date;

/**
 * @author Nishesh Pandey
 */

public class RecurringTransactionUtils {

    private static PaymentCachingService cachingService;

    @Autowired
    public void load(PaymentCachingService cachingService) {
        this.cachingService=cachingService;
    }

    public static BillingUtils getBillingUtils(PlanDTO selectedPlan, boolean isFreeTrial) {
        int validTillDays = Math.toIntExact(selectedPlan.getPeriod().getTimeUnit().toDays(selectedPlan.getPeriod().getValidity()));
        int freeTrialValidity = isFreeTrial ? cachingService.getPlan(selectedPlan.getLinkedFreePlanId()).getPeriod().getValidity() : validTillDays;
        return freeTrialValidity == validTillDays ? new BillingUtils(validTillDays) : new BillingUtils(1, BillingCycle.ADHOC);
    }

    public static String generateInvoiceNumber() {
        long invoiceNumber = (long) (Math.random() * 100000000000000000L);
        return invoiceNumber+"";
    }
}