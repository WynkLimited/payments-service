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
public class PaytmBalanceRequestBody extends PaytmRequestBody {

    private String userToken;
    private double txnAmount;
    private Object subwalletAmount;
    private boolean showExpiredMerchantGVBalance;

}