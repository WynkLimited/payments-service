package in.wynk.payment.dto.response.presentation.upi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SeamlessUpiChargingResponse extends AbstractUpiChargingResponse {
    @JsonProperty("package")
    private String appPackage;
    @JsonProperty("info")
    private String deeplink;
}
