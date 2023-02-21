package in.wynk.payment.dto.aps.response.verify;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@Builder
public class ApsBinVerificationResponseData {
    private String cardBin;
    private String cardNetwork;
    private String cardCategory;
    private String bankCode;
    private String cvvLength;
    private String bankName;
    private boolean blocked;
    private String healthState;
    private boolean autoPayEnable;
    private String paymentMode;
    private boolean domestic;
}
