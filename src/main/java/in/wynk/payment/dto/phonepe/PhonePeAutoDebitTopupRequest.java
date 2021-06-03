package in.wynk.payment.dto.phonepe;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PhonePeAutoDebitTopupRequest extends PhonePeAutoDebitRequest{

    private long amount;
    private String linkType;
    private boolean adjustAmount;
    private DeviceContext deviceContext;

}