package in.wynk.payment.dto.response.paymentoption;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.vas.client.dto.atb.LinkedSis;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@SuperBuilder
public class BillingSavedDetails extends AbstractSavedPaymentDTO {
    @JsonProperty("linked_sis")
    private final List<LinkedSis> linkedSis;
}
