package in.wynk.payment.dto.request;

import in.wynk.payment.dto.request.billing.BillingFeatures;
import in.wynk.payment.dto.request.billing.BillingSavedDetails;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
public class Billing {
    private String id;
    private String code;
    private String title;
    private String description;
    private List<BillingFeatures> features;
    private BillingSavedDetails savedDetails;
    private PromotionDetails promotonDetails;
    private UIDetails uiDetails;
}
