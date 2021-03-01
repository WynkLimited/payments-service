package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.StatusMode;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

@Getter
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class ChargingStatusRequest {

    @Setter
    @ApiModelProperty(hidden = true)
    private int planId;
    private StatusMode mode;
    private String transactionId;

}
