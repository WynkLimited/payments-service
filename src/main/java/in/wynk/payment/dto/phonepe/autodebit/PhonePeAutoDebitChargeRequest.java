package in.wynk.payment.dto.phonepe.autodebit;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.phonepe.autodebit.DeviceContext;
import in.wynk.payment.dto.request.ChargingRequest;
import lombok.*;
import lombok.experimental.SuperBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@ToString
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PhonePeAutoDebitChargeRequest extends ChargingRequest {
    private String merchantId;
    private String userAuthToken;
    private String transactionId;
    private long amount;
    private Long phonePeVersionCode;

}
