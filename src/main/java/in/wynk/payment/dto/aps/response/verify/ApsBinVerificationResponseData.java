package in.wynk.payment.dto.aps.response.verify;

import in.wynk.payment.dto.aps.common.TokenizationConfig;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

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
    private TokenizationConfig tokenizationConfig;
    private String blockedReason;
}
