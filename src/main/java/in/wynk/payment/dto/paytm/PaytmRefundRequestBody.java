package in.wynk.payment.dto.paytm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaytmRefundRequestBody extends PaytmStatusRequestBody {

    private String txnId;
    private String comments;
    private String refundAmount;
    private String preferredDestination;

}