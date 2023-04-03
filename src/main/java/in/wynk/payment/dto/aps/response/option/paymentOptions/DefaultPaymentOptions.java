package in.wynk.payment.dto.aps.response.option.paymentOptions;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Collections;
import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@NoArgsConstructor
public class DefaultPaymentOptions extends AbstractPaymentOptions {
    @Override
    public <T extends ISubOption> List<T> getOption() {
        return Collections.emptyList();
    }
}
