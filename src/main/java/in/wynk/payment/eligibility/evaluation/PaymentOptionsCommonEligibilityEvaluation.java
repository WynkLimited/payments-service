package in.wynk.payment.eligibility.evaluation;

import in.wynk.data.entity.MongoBaseEntity;
import in.wynk.eligibility.dto.AbstractEligibilityEvaluation;
import in.wynk.eligibility.dto.EligibilityResult;
import in.wynk.eligibility.enums.EligibilityStatus;
import in.wynk.payment.eligibility.enums.PaymentMethodEligibilityReason;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.Optional;

@Slf4j
@Getter
@SuperBuilder
public abstract class PaymentOptionsCommonEligibilityEvaluation<T extends MongoBaseEntity<String>, P extends PaymentOptionsEligibilityRequest> extends AbstractEligibilityEvaluation<T,P> {

    public boolean msisdnStartsWithCodes(String... countryCodes) {
        final EligibilityResult.EligibilityResultBuilder<T> resultBuilder = EligibilityResult.<T>builder().entity(getEntity()).status(EligibilityStatus.NOT_ELIGIBLE);
        try {
            final PaymentOptionsEligibilityRequest root = getRoot();
            final String msisdn = root.getMsisdn();
            if(StringUtils.isEmpty(msisdn)) {
                resultBuilder.reason(PaymentMethodEligibilityReason.BLANK_MSISDN);
            } else {
                final Optional<String> msisdnWithCountryCode = Arrays.stream(countryCodes).filter(countryCode -> msisdn.startsWith(countryCode)).findAny();
                if (!msisdnWithCountryCode.isPresent()) {
                    resultBuilder.reason(PaymentMethodEligibilityReason.MSISDN_NOT_IN_COUNTRY_CODE);
                } else {
                    resultBuilder.status(EligibilityStatus.ELIGIBLE);
                }
            }
            return resultBuilder.build().isEligible();
        } finally {
            result = resultBuilder.build();
        }
    }

    public boolean hasServiceIn(String... services) {
        final EligibilityResult.EligibilityResultBuilder<T> resultBuilder = EligibilityResult.<T>builder().entity(getEntity()).status(EligibilityStatus.NOT_ELIGIBLE);
        try {
            final PaymentOptionsEligibilityRequest root = getRoot();
            final String service = root.getService();
            if(StringUtils.isEmpty(service)) {
                resultBuilder.reason(PaymentMethodEligibilityReason.BLANK_SERVICE);
            } else {
                final Optional<String> msisdnWithCountryCode = Arrays.stream(services).filter(providedService -> service.equalsIgnoreCase(providedService)).findAny();
                if (!msisdnWithCountryCode.isPresent()) {
                    resultBuilder.reason(PaymentMethodEligibilityReason.SERVICE_NOT_IN_PROVIDED_LIST);
                } else {
                    resultBuilder.status(EligibilityStatus.ELIGIBLE);
                }
            }
            return resultBuilder.build().isEligible();
        } finally {
            result = resultBuilder.build();
        }
    }
}
