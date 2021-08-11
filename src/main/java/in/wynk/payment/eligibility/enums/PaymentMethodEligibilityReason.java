package in.wynk.payment.eligibility.enums;

import in.wynk.eligibility.enums.IEligibilityStatusReason;

public enum PaymentMethodEligibilityReason implements IEligibilityStatusReason {
    MSISDN_NOT_IN_COUNTRY_CODE,
    BLANK_MSISDN,
    BLANK_SERVICE,
    SERVICE_NOT_IN_PROVIDED_LIST;

    @Override
    public String getCode() {
        return this.name();
    }
}
