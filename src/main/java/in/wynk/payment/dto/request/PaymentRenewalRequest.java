package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import in.wynk.payment.dto.request.Apb.ApbPaymentRenewalRequest;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ApbPaymentRenewalRequest.class, name = "ApbPaymentRenewal")
})
public class PaymentRenewalRequest {

}
