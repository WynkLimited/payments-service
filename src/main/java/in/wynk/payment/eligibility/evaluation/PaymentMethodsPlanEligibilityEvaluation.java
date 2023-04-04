package in.wynk.payment.eligibility.evaluation;

import in.wynk.common.constant.BaseConstants;
import in.wynk.eligibility.dto.EligibilityResult;
import in.wynk.eligibility.enums.CommonEligibilityStatusReason;
import in.wynk.eligibility.enums.EligibilityStatus;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.dto.common.AbstractPaymentInstrumentsProxy;
import in.wynk.payment.dto.common.AbstractPaymentOptionInfo;
import in.wynk.payment.eligibility.enums.PaymentsEligibilityReason;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.util.List;

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
                final AbstractPaymentInstrumentsProxy proxy = getRoot().getPaymentInstrumentsProxy(pg, msisdn);
                final List<AbstractPaymentOptionInfo> payOption = proxy.getPaymentInstruments(msisdn);
                try {
                    final boolean isEligible = payOption.stream().filter(option -> option.getType().equalsIgnoreCase(groupId)).filter(AbstractPaymentOptionInfo::isEnabled).map(AbstractPaymentOptionInfo::getId).anyMatch(optionId -> payId.equalsIgnoreCase(optionId));
                    if (isEligible) {
                        resultBuilder.status(EligibilityStatus.ELIGIBLE);
                    } else {
                        resultBuilder.reason(PaymentsEligibilityReason.NOT_EXTERNAL_ELIGIBLE);
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
