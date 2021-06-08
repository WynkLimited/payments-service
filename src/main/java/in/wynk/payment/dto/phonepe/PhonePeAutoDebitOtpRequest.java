package in.wynk.payment.dto.phonepe;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhonePeAutoDebitOtpRequest extends PhonePeAutoDebitRequest {

    private String otp;
    private String otpToken;

}