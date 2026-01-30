package in.wynk.payment.dto.phonepe;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.phonepe.autodebit.DeviceContext;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Deprecated
public class PhonePeAutoDebitTopupRequest extends PhonePeAutoDebitRequest{

    private long amount;
    private String linkType;
    private boolean adjustAmount;
    private DeviceContext deviceContext;

}