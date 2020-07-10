package in.wynk.payment.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentResponse {
    private UserData userData;
    private Receipt receipt;
}
