package in.wynk.payment.eligibility.evaluation;

import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.dao.entity.PaymentMethod;
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
}
