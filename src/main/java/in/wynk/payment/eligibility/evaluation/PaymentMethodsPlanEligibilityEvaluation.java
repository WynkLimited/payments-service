package in.wynk.payment.eligibility.evaluation;

import in.wynk.common.constant.BaseConstants;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.eligibility.dto.EligibilityResult;
import in.wynk.eligibility.enums.CommonEligibilityStatusReason;
import in.wynk.eligibility.enums.EligibilityStatus;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.dto.IPaymentOptionEligibility;
import in.wynk.payment.eligibility.enums.PaymentsEligibilityReason;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

@Slf4j
@Getter
@SuperBuilder
public class PaymentMethodsPlanEligibilityEvaluation extends PaymentOptionsCommonEligibilityEvaluation<PaymentMethod, PaymentOptionsEligibilityRequest>  {

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
            final PaymentOptionsEligibilityRequest root = getRoot();
            if (StringUtils.isBlank(root.getMsisdn())) {
                resultBuilder.reason(CommonEligibilityStatusReason.MSISDN_REQUIRED);
            } else {
                final String payId = getEntity().getId();
                final String msisdn = getRoot().getMsisdn();
                final String groupId = getEntity().getGroup();
                final String pg = getEntity().getPaymentCode().getCode();
                final boolean isExternalEligible = BeanLocatorFactory.getBean(pg, IPaymentOptionEligibility.class).isEligible(msisdn, groupId, payId);
                if (isExternalEligible) {
                    resultBuilder.status(EligibilityStatus.ELIGIBLE);
                } else {
                    resultBuilder.reason(PaymentsEligibilityReason.NOT_EXTERNAL_ELIGIBLE);
                }
            }
            return resultBuilder.build().isEligible();
        } finally {
            result = resultBuilder.build();
        }
    }
}
