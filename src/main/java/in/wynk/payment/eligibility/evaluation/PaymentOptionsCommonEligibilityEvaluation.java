package in.wynk.payment.eligibility.evaluation;

import in.wynk.common.constant.BaseConstants;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.country.core.dao.entity.CountryCurrencyDetails;
import in.wynk.country.core.service.CountryCurrencyDetailsCachingService;
import in.wynk.data.entity.MongoBaseEntity;
import in.wynk.data.enums.State;
import in.wynk.eligibility.dto.AbstractEligibilityEvaluation;
import in.wynk.eligibility.dto.EligibilityResult;
import in.wynk.eligibility.enums.CommonEligibilityStatusReason;
import in.wynk.eligibility.enums.EligibilityStatus;
import in.wynk.payment.eligibility.enums.PaymentsEligibilityReason;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsItemEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;
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
public abstract class PaymentOptionsCommonEligibilityEvaluation<T extends MongoBaseEntity<String>, P extends PaymentOptionsEligibilityRequest> extends AbstractEligibilityEvaluation<T, P> {

    public boolean isAirtelUser() {
        final EligibilityResult.EligibilityResultBuilder<T> resultBuilder = EligibilityResult.<T>builder().entity(getEntity()).status(EligibilityStatus.NOT_ELIGIBLE);
        try {
            final PaymentOptionsEligibilityRequest root = getRoot();
            if (StringUtils.isBlank(root.getMsisdn())) {
                resultBuilder.reason(CommonEligibilityStatusReason.MSISDN_REQUIRED);
            } else {
                final boolean isAirtelUser = root.getPaymentOptionsEligibilityRequestProxy().isAirtelUser(root.getMsisdn(),root.getService());
                if (isAirtelUser) {
                    resultBuilder.status(EligibilityStatus.ELIGIBLE);
                } else {
                    resultBuilder.reason(CommonEligibilityStatusReason.IS_NON_AIRTEL_USER);
                }
            }
            return resultBuilder.build().isEligible();
        } finally {
            result = resultBuilder.build();
        }
    }

    public boolean liesInSegment(String... segments) {
        final EligibilityResult.EligibilityResultBuilder<T> resultBuilder = EligibilityResult.<T>builder().entity(getEntity()).status(EligibilityStatus.NOT_ELIGIBLE);
        try {
            final PaymentOptionsEligibilityRequest root = getRoot();
            if (StringUtils.isBlank(root.getMsisdn())) {
                resultBuilder.reason(CommonEligibilityStatusReason.MSISDN_REQUIRED);
            } else if (StringUtils.isBlank(root.getService())) {
                resultBuilder.reason(PaymentsEligibilityReason.BLANK_SERVICE);
            } else {
                Set<String> thanksSegments = root.getPaymentOptionsEligibilityRequestProxy().getThanksSegments(root.getMsisdn(),root.getService());
                Optional<String> foundSegment = Arrays.stream(segments).filter(thanksSegments::contains).findAny();
                if (foundSegment.isPresent()) {
                    resultBuilder.status(EligibilityStatus.ELIGIBLE);
                } else {
                    resultBuilder.reason(CommonEligibilityStatusReason.THANKS_COHORT_NOT_ELIGIBLE);
                }
            }
            return resultBuilder.build().isEligible();
        } finally {
            result = resultBuilder.build();
        }
    }

    public boolean hasCountryCode(String... codes) {
        final EligibilityResult.EligibilityResultBuilder<T> resultBuilder = EligibilityResult.<T>builder().entity(getEntity()).status(EligibilityStatus.NOT_ELIGIBLE);
        try {
            final PaymentOptionsEligibilityRequest root = getRoot();
            final String countryCode = root.getCountryCode();
            if (StringUtils.isBlank(countryCode)) {
                resultBuilder.reason(CommonEligibilityStatusReason.EMPTY_COUNTRY_CODE);
            } else {
                Set<String> countryCurrencyDetails = BeanLocatorFactory.getBean(CountryCurrencyDetailsCachingService.class).getAllByState(State.ACTIVE).stream().map(CountryCurrencyDetails::getCountryCode).collect(Collectors.toSet());
                Optional<String> validCountryCode = Arrays.stream(codes).filter(countryCurrencyDetails::contains).filter(countryCurrencyDetail -> countryCurrencyDetail.equals(countryCode)).findAny();
                if (validCountryCode.isPresent()) {
                    resultBuilder.status(EligibilityStatus.ELIGIBLE);
                } else {
                    resultBuilder.reason(CommonEligibilityStatusReason.INVALID_COUNTRY_CODE);
                }
            }
            return resultBuilder.build().isEligible();
        } finally {
            result = resultBuilder.build();
        }
    }

    public boolean msisdnStartsWithCodes(String... countryCodes) {
        final EligibilityResult.EligibilityResultBuilder<T> resultBuilder = EligibilityResult.<T>builder().entity(getEntity()).status(EligibilityStatus.NOT_ELIGIBLE);
        try {
            final PaymentOptionsEligibilityRequest root = getRoot();
            final String msisdn = root.getMsisdn();
            if (StringUtils.isEmpty(msisdn)) {
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
            if (StringUtils.isEmpty(service)) {
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
                final Set<String> availableAppIds = Arrays.stream(appIds).map(WynkServiceUtils::fromAppId).map(app -> app.getId()).collect(Collectors.toSet());
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
                if (!os.equalsIgnoreCase(currentOsName)) {
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

    public boolean hasCouponId(String... coupons) {
        final EligibilityResult.EligibilityResultBuilder<T> resultBuilder = EligibilityResult.<T>builder().entity(getEntity()).status(EligibilityStatus.NOT_ELIGIBLE);
        try {
            final PaymentOptionsEligibilityRequest root = getRoot();
            final String couponId = root.getCouponCode();
            if (StringUtils.isBlank(couponId)) {
                resultBuilder.reason(PaymentsEligibilityReason.EMPTY_COUPON_ID);
            } else {
                Optional<String> validCountryCode = Arrays.stream(coupons).filter(coupon -> couponId.equals(coupon)).findAny();
                if (validCountryCode.isPresent()) {
                    resultBuilder.status(EligibilityStatus.ELIGIBLE);
                } else {
                    resultBuilder.reason(PaymentsEligibilityReason.INVALID_COUPON);
                }
            }
            return resultBuilder.build().isEligible();
        } finally {
            result = resultBuilder.build();
        }
    }

    public boolean hasPlanId(Integer... planIds) {
        final EligibilityResult.EligibilityResultBuilder<T> resultBuilder = EligibilityResult.<T>builder().entity(getEntity()).status(EligibilityStatus.NOT_ELIGIBLE);
        try {
            if(getRoot() instanceof PaymentOptionsPlanEligibilityRequest) {
                final PaymentOptionsPlanEligibilityRequest root = (PaymentOptionsPlanEligibilityRequest) getRoot();
                final Integer activePlanId = Integer.valueOf(root.getPlanId());
                final Optional<Integer> planIdOption = Arrays.stream(planIds).filter(activePlanId::equals).findAny();
                if (!planIdOption.isPresent()) {
                    resultBuilder.reason(PaymentsEligibilityReason.NOT_IN_PLAN_LIST);
                } else {
                    resultBuilder.status(EligibilityStatus.ELIGIBLE);
                }
            } else if(getRoot() instanceof PaymentOptionsItemEligibilityRequest) {
                resultBuilder.status(EligibilityStatus.ELIGIBLE);
            }
            return resultBuilder.build().isEligible();
        } finally {
            result = resultBuilder.build();
        }
    }

    public boolean hasItemId(Integer... itemIds) {
        final EligibilityResult.EligibilityResultBuilder<T> resultBuilder = EligibilityResult.<T>builder().entity(getEntity()).status(EligibilityStatus.NOT_ELIGIBLE);
        try {
            if(getRoot() instanceof  PaymentOptionsItemEligibilityRequest) {
                final PaymentOptionsItemEligibilityRequest root = (PaymentOptionsItemEligibilityRequest) getRoot();
                final Integer activeItemId = Integer.valueOf(root.getItemId());
                final Optional<Integer> itemIdOption = Arrays.stream(itemIds).filter(activeItemId::equals).findAny();
                if (!itemIdOption.isPresent()) {
                    resultBuilder.reason(PaymentsEligibilityReason.NOT_IN_ITEM_LIST);
                } else {
                    resultBuilder.status(EligibilityStatus.ELIGIBLE);
                }
            } else if(getRoot() instanceof  PaymentOptionsPlanEligibilityRequest) {
                resultBuilder.status(EligibilityStatus.ELIGIBLE);
            }
            return resultBuilder.build().isEligible();
        } finally {
            result = resultBuilder.build();
        }
    }

    public boolean isMsisdnInRange (int start, int end) {
        final EligibilityResult.EligibilityResultBuilder<T> resultBuilder = EligibilityResult.<T>builder().entity(getEntity()).status(EligibilityStatus.NOT_ELIGIBLE);
        if (start < 0 || end > 9) {
            resultBuilder.reason(CommonEligibilityStatusReason.UNSUPPORTED_RANGE);
        }
        try {
            final PaymentOptionsEligibilityRequest root = getRoot();
            if (start <= root.getMsisdnRangeProbability() && root.getMsisdnRangeProbability() <= end) {
                resultBuilder.status(EligibilityStatus.ELIGIBLE);
            } else {
                resultBuilder.reason(PaymentsEligibilityReason.MSISDN_NOT_IN_RANGE);
            }
            return resultBuilder.build().isEligible();
        } finally {
            result = resultBuilder.build();
        }
    }
}