package in.wynk.payment.dto.request.charge.upi;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;
import in.wynk.payment.dto.request.common.UpiDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Objects;

import static in.wynk.payment.constant.UpiConstants.UPI;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class UpiPaymentDetails extends AbstractPaymentDetails {

    private UpiDetails upiDetails;

    public boolean isIntent() {
        return Objects.nonNull(upiDetails) && upiDetails.isIntent();
    }

    @Override
    public String getPaymentMode() {
        return UPI;
    }
}
