package in.wynk.payment.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.wynk.payment.dto.request.IapVerificationRequest;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AmazonIapVerificationRequest extends IapVerificationRequest {
    private UserData userData;
    private Receipt receipt;
    private String service;
}
