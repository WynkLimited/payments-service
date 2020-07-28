package in.wynk.payment.dto.itune;

import in.wynk.payment.dto.request.IapVerificationRequest;
import lombok.Getter;

@Getter
public class ItunesVerificationRequest extends IapVerificationRequest {
    String receipt;
}
