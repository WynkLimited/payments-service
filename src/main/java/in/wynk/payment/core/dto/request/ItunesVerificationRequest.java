package in.wynk.payment.core.dto.request;

import lombok.Getter;

@Getter
public class ItunesVerificationRequest extends IapVerificationRequest{
    String receipt;
}
