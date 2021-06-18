package in.wynk.payment.dto.response.paytm;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaytmPayOption {

    private double amount;
    private String payMethod;
    private String displayName;
    private double deficitAmount;
    private double expiredAmount;
    private boolean fundSufficient;
    private boolean addMoneyAllowed;

}
