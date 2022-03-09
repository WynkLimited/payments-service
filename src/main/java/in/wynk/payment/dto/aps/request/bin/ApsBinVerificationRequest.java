package in.wynk.payment.dto.aps.request.bin;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 *  @purpose This service is usedto fetch the card details using card bin, this also tells whether SI is eligible on given card
 */
@Getter
@Builder
@ToString
public class ApsBinVerificationRequest {
    private String cardBin;
    private String lob;
    private String subLob;
}
