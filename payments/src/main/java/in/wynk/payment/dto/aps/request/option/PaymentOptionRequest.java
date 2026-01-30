package in.wynk.payment.dto.aps.request.option;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.common.constant.BaseConstants;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentOptionRequest {
    @Builder.Default
    private String lob = BaseConstants.WYNK;
}
