package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class S2SPaymentOptionsRequest implements IPaymentOptionsRequest {

    @Analysed
    private String couponId;
    @Analysed
    private AppDetails appDetails;
    @Analysed
    private UserDetails userDetails;
    @Analysed
    private AbstractProductDetails productDetails;

}