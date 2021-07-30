package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IPaymentDetails;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class ChargingDetails implements IChargingDetails {

    @Analysed
    private String callbackUrl;
    @Analysed
    private IAppDetails appDetails;
    @Analysed
    private IUserDetails userDetails;
    @Analysed
    private IPaymentDetails paymentDetails;
    @Analysed
    private IPageUrlDetails pageUrlDetails;
    @Analysed
    private IProductDetails productDetails;

    @Override
    @JsonIgnore
    public ICallbackDetails getCallbackDetails() {
        return () -> callbackUrl;
    }
}
