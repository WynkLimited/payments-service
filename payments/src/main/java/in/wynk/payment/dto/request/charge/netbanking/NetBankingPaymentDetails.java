package in.wynk.payment.dto.request.charge.netbanking;

import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;
import in.wynk.payment.dto.request.common.ENACHMandateInfo;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import static in.wynk.payment.constant.NetBankingConstants.NET_BANKING;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class NetBankingPaymentDetails extends AbstractPaymentDetails {

    private ENACHMandateInfo mandateInfo;

    @Override
    public String getPaymentMode() {
        return NET_BANKING;
    }
}
