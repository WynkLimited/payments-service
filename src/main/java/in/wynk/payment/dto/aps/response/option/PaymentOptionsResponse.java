package in.wynk.payment.dto.aps.response.option;

import in.wynk.payment.dto.aps.response.option.paymentOptions.AbstractPaymentOptions;
import in.wynk.payment.dto.aps.response.option.paymentOptions.PaymentOptionsConfig;
import in.wynk.payment.dto.aps.response.option.savedOptions.SavedUserOptions;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.List;

@Getter
@SuperBuilder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOptionsResponse implements Serializable {
    private PaymentOptionsConfig config;
    private SavedUserOptions savedUserOptions;
    private List<AbstractPaymentOptions> payOptions;
}
