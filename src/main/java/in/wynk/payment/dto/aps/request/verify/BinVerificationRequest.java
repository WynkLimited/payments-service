package in.wynk.payment.dto.aps.request.verify;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.common.constant.BaseConstants;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 *  @purpose This service is usedto fetch the card details using card bin, this also tells whether SI is eligible on given card
 */
@Getter
@Builder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BinVerificationRequest {
    private String cardBin;
    @Builder.Default
    private String lob = BaseConstants.WYNK;
    private String subLob;
}
