package in.wynk.payment.eligibility.evaluation;

import in.wynk.common.constant.BaseConstants;
import in.wynk.data.entity.MongoBaseEntity;
import in.wynk.eligibility.dto.AbstractEligibilityEvaluation;
import in.wynk.eligibility.dto.EligibilityResult;
import in.wynk.eligibility.enums.CommonEligibilityStatusReason;
import in.wynk.eligibility.enums.EligibilityStatus;
import in.wynk.payment.eligibility.enums.PaymentsEligibilityReason;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
                resultBuilder.reason(PaymentsEligibilityReason.BLANK_MSISDN);
            } else {
                final Optional<String> msisdnWithCountryCode = Arrays.stream(countryCodes).filter(countryCode -> msisdn.startsWith(countryCode)).findAny();
                if (!msisdnWithCountryCode.isPresent()) {
                    resultBuilder.reason(PaymentsEligibilityReason.MSISDN_NOT_IN_COUNTRY_CODE);
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
                resultBuilder.reason(PaymentsEligibilityReason.BLANK_SERVICE);
            } else {
                final Optional<String> msisdnWithCountryCode = Arrays.stream(services).filter(providedService -> service.equalsIgnoreCase(providedService)).findAny();
                if (!msisdnWithCountryCode.isPresent()) {
                    resultBuilder.reason(PaymentsEligibilityReason.SERVICE_NOT_IN_PROVIDED_LIST);
                } else {
                    resultBuilder.status(EligibilityStatus.ELIGIBLE);
                }
            }
            return resultBuilder.build().isEligible();
        } finally {
            result = resultBuilder.build();
        }
    }

    public boolean hasAppId(String... appIds) {
        final EligibilityResult.EligibilityResultBuilder<T> resultBuilder = EligibilityResult.<T>builder().entity(getEntity()).status(EligibilityStatus.NOT_ELIGIBLE);
        try {
            final PaymentOptionsEligibilityRequest root = getRoot();
            final String appId = root.getAppId();
            if (appId == null || appId.equals(BaseConstants.UNKNOWN)) {
                resultBuilder.reason(CommonEligibilityStatusReason.APP_ID_REQUIRED);
            } else {
                final Set<String> availableAppIds = Arrays.stream(appIds).map(WynkServiceUtils::fromAppId).map(app->app.getId()).collect(Collectors.toSet());
                if (CollectionUtils.isNotEmpty(availableAppIds) && availableAppIds.contains(appId)) {
                    resultBuilder.status(EligibilityStatus.ELIGIBLE);
                } else {
                    resultBuilder.reason(CommonEligibilityStatusReason.NOT_ELIGIBLE_FOR_APP_ID);
                }
            }
            return resultBuilder.build().isEligible();
        } finally {
            result = resultBuilder.build();
        }
    }

    public boolean buildNumber(String os, Integer minBuildNum, Integer maxBuildNum) {
        final EligibilityResult.EligibilityResultBuilder<T> resultBuilder = EligibilityResult.<T>builder().entity(getEntity()).status(EligibilityStatus.NOT_ELIGIBLE);
        try {
            final PaymentOptionsEligibilityRequest root = getRoot();
            final String currentOsName = root.getOs();
            if (currentOsName == null) {
                resultBuilder.reason(CommonEligibilityStatusReason.OS_REQUIRED);
            } else {
                if (!os.equals(currentOsName)) {
                    resultBuilder.reason(CommonEligibilityStatusReason.UNSUPPORTED_OS);
                } else {
                    final Integer currentBuildNo = root.getBuildNo();
                    if (minBuildNum > 0 && maxBuildNum > 0 && isBuildNoInRange(minBuildNum, maxBuildNum, currentBuildNo)) {
                        resultBuilder.status(EligibilityStatus.ELIGIBLE);
                    } else if (minBuildNum < 0 && isBuildNoInRange(Integer.MIN_VALUE, maxBuildNum, currentBuildNo)) {
                        resultBuilder.status(EligibilityStatus.ELIGIBLE);
                    } else if (maxBuildNum < 0 && isBuildNoInRange(minBuildNum, Integer.MAX_VALUE, currentBuildNo)) {
                        resultBuilder.status(EligibilityStatus.ELIGIBLE);
                    } else {
                        resultBuilder.reason(CommonEligibilityStatusReason.NOT_ELIGIBLE_FOR_BUILD_NO);
                    }
                }
            }
            return resultBuilder.build().isEligible();
        } finally {
            result = resultBuilder.build();
        }
    }


    private boolean isBuildNoInRange(Integer minBuildNum, Integer maxBuildNum, Integer currentBuildNo) {
        return currentBuildNo >= minBuildNum && currentBuildNo <= maxBuildNum;
    }

}
