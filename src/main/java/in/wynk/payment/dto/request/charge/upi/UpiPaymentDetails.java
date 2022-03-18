package in.wynk.payment.dto.request.charge.upi;

import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.dto.request.common.UpiDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class UpiPaymentDetails extends AbstractPaymentDetails {

    private UpiDetails upiDetails;

    @Override
    public String getPaymentGroup() {
        return PaymentConstants.UPI;
    }
}
