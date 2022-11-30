package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
public class PaymentOption extends AbstractPaymentOption {
    private List<SubPaymentOption> subOptionList;
}
