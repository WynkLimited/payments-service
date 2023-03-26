package in.wynk.payment.dto.response.paymentoption;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
public class UpiSavedDetails extends AbstractSavedPaymentDTO {
    @JsonProperty("vpa_token_id")
    private String vpaTokenId;
    private String vpa;

}
