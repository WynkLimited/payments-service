package in.wynk.payment.eligibility.enums;

import in.wynk.eligibility.enums.IEligibilityStatusReason;

public enum PaymentsEligibilityReason implements IEligibilityStatusReason {
    MSISDN_NOT_IN_COUNTRY_CODE,
    BLANK_MSISDN,
    SERVICE_NOT_IN_PROVIDED_LIST,
    BLANK_SERVICE,
    EMPTY_COUNTRY_CODE,
    INVALID_COUNTRY_CODE,
    EMPTY_COUPON_ID,
    INVALID_COUPON
    ;

    @Override
    public String getCode() {
        return this.name();
    }
}
