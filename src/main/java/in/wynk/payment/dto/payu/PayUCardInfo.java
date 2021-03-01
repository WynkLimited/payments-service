package in.wynk.payment.dto.payu;

import lombok.Getter;
import lombok.Setter;

@Getter
public class PayUCardInfo {

    private String issuingBank;

    private String isDomestic;

    private String cardType;

    private String cardCategory;

    @Setter
    private boolean isValid;

}
