package in.wynk.payment.dto.apb.paytm;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.response.apb.paytm.APBPaytmResponseData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionStatusAPBPaytmResponse extends AbstractAPBPaytmResponse {

    private APBPaytmResponseData data[];
}
