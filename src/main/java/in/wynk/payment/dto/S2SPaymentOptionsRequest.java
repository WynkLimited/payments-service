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
    private AppDetails appDetails;
    @Analysed
    private UserDetails userDetails;
    private String planId;
    private String itemId;
    private String couponId;
    private String countryCode;
}
