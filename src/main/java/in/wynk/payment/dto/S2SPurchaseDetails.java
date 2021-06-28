package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class S2SPurchaseDetails implements IPurchaseDetails {
    @Analysed
    private AppDetails appDetails;
    @Analysed
    private UserDetails userDetails;
    @Analysed
    private PaymentDetails paymentDetails;
    @Analysed
    private AbstractProductDetails productDetails;
}
