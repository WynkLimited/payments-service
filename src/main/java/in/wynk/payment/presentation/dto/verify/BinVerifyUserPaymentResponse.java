package in.wynk.payment.presentation.dto.verify;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BinVerifyUserPaymentResponse extends VerifyUserPaymentResponse {
    private boolean inAppOtpSupport;
    private String cardCategory;
    private String cardType;
    private String isDomestic;
    private boolean autoRenewSupported;
}
