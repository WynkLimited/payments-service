package in.wynk.payment.eligibility.evaluation;

import in.wynk.common.constant.BaseConstants;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.eligibility.dto.EligibilityResult;
import in.wynk.eligibility.enums.CommonEligibilityStatusReason;
import in.wynk.eligibility.enums.EligibilityStatus;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.eligibility.enums.PaymentsEligibilityReason;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsItemEligibilityRequest;
import in.wynk.payment.service.IExternalPaymentEligibilityService;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.ParameterizedTypeReference;

@Slf4j
@Getter
@SuperBuilder
public class PaymentMethodsItemEligibilityEvaluation extends PaymentOptionsCommonEligibilityEvaluation<PaymentMethod, PaymentOptionsEligibilityRequest>  {

    @Override
    public String getExpression() {
        if (StringUtils.isEmpty(getEntity().getRuleExpression())) {
            return BaseConstants.TRUE;
        }
        return getEntity().getRuleExpression();
    }

    public boolean isExternalEligible() {
        final EligibilityResult.EligibilityResultBuilder<PaymentMethod> resultBuilder = EligibilityResult.<PaymentMethod>builder().entity(getEntity()).status(EligibilityStatus.NOT_ELIGIBLE);
        try {
            final PaymentOptionsItemEligibilityRequest root = (PaymentOptionsItemEligibilityRequest) getRoot();
            if (StringUtils.isBlank(root.getMsisdn())) {
                resultBuilder.reason(CommonEligibilityStatusReason.MSISDN_REQUIRED);
            } else {
                try {
                    final boolean isExternalEligible = BeanLocatorFactory.getBean(getEntity().getPaymentCode().getCode(), new ParameterizedTypeReference<IExternalPaymentEligibilityService>() {
                    }).isEligible(getEntity(), root);
                    if (isExternalEligible) {
                        resultBuilder.status(EligibilityStatus.ELIGIBLE);
                    } else {
                        resultBuilder.reason(CommonEligibilityStatusReason.NOT_ELIGIBLE_FOR_ADDTOBILL);
                    }
                } catch (Exception e) {
                    log.error(PaymentLoggingMarker.EXTERNAL_ELIGIBILITY_FAILURE, "unable to evaluate eligibility from external source", e);
                    resultBuilder.reason(PaymentsEligibilityReason.NOT_EXTERNAL_ELIGIBLE);
                }
            }
            return resultBuilder.build().isEligible();
        } finally {
            result = resultBuilder.build();
        }
    }
}
