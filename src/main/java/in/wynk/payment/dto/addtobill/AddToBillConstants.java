package in.wynk.payment.dto.addtobill;

public interface AddToBillConstants {
    String DTH = "DTH";
    String POSTPAID = "POSTPAID";
    String MOBILITY = "Mobility";
    String TELEMEDIA = "Telemedia";
    String DETAILS = "DETAILS";
    String BILL = "BILL";
    String UNIQUE_TRACKING= "uniqueTracking";
    String CHECK_ELIGIBILITY = "checkEligibility";
    String SI = "si";
    String CHANNEL = "channel";
    String ADDTOBILL = "ADDTOBILL";

    String ADDTOBILL_ELIGIBILITY_API="/shop-eligibility/getDetailsEligibilityAndPricing";
    String ADDTOBILL_CHECKOUT_API="/orderhive/s2s/auth/api/order/proceedToCheckout";
    String ADDTOBILL_ORDER_STATUS_API="/emporio/getUserOrders";
    String ADDTOBILL_UNSUBSCRIBE_API="/enigma/v2/unsubscription";


}
