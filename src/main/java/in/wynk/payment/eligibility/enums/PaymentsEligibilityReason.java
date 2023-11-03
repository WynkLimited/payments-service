package in.wynk.payment.eligibility.enums;

import in.wynk.eligibility.enums.IEligibilityStatusReason;

public enum PaymentsEligibilityReason implements IEligibilityStatusReason {
    MSISDN_NOT_IN_COUNTRY_CODE,
    BLANK_MSISDN,
    SERVICE_NOT_IN_PROVIDED_LIST,
    BLANK_SERVICE,
    EMPTY_COUPON_ID,
    INVALID_COUPON,
    NOT_IN_PLAN_LIST,
    NOT_EXTERNAL_ELIGIBLE,
    NOT_IN_ITEM_LIST,
    MSISDN_NOT_IN_RANGE;

    @Override
    public String getCode() {
        return this.name();
    }
}
