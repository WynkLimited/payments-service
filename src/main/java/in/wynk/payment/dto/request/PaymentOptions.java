package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class PaymentOptions {
    @JsonProperty("pay_group_details")
    private List<PayGroupDetails> payGroupDetails;
}
