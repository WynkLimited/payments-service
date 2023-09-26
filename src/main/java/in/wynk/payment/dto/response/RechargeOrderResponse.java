package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class RechargeOrderResponse extends AbstractRechargeOrderResponse{
    private String orderId;
    private String checksum;
}
