package in.wynk.payment.dto.phonepe;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.SuperBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@ToString
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PhonePeAutoDebitTopupRequest extends PhonePeAutoDebitRequest{
    private long amount;
    private boolean adjustAmount;
    private String linkType;
    private DeviceContext deviceContext;
}
