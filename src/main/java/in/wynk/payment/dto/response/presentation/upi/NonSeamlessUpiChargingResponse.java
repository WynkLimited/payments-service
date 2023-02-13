package in.wynk.payment.dto.response.presentation.upi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class NonSeamlessUpiChargingResponse extends AbstractUpiChargingResponse {
    @JsonProperty("package")
    private String appPackage;
    @JsonProperty("info")
    private String form;
}
