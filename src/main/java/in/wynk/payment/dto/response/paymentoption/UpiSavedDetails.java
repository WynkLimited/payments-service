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
@NoArgsConstructor
public class UpiSavedDetails extends SavedPaymentDTO {
    /*@JsonProperty("vpa_token_id")
    private String vpaTokenId;*///No such field exist
    private String vpa;

}
