package in.wynk.payment.dto.aps.response.option;

import in.wynk.common.dto.IErrorDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@SuperBuilder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ApsPaymentOptionsResponse {
    private PaymentOptionsConfig config;
    private SavedUserOptions savedUserOptions;
    private List<AbstractPaymentOptions> payOptions;
}
