package in.wynk.payment.dto.request;

import lombok.Getter;

@Getter
public class ItunesVerificationRequest extends IapVerificationRequest{
    String receipt;
}
