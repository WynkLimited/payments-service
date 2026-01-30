package in.wynk.payment.dto.response.paymentoption;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
public class UpiSavedDetails extends AbstractSavedPaymentDTO {
    private String vpa;
    @JsonProperty("package_name")
    private String packageName;
}
