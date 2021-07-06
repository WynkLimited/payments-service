package in.wynk.payment.dto.apb.paytm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.SuperBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@SuperBuilder
@Getter
public class APBPaytmPaymentInfo extends APBPaytmRequest{
    private String lob;
    private String encryptedToken;
    private String currency;
    private String paymentMode;
    private double paymentAmount;
}
