package in.wynk.payment.dto.phonepe.autodebit;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PhonePeAutoDebitValidateOtpRequest {
    private String otp;
    private String otpToken;
    private String merchantId;
}
