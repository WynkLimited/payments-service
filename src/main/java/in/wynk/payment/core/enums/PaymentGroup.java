package in.wynk.payment.core.enums;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;

@Deprecated
@AnalysedEntity
@AllArgsConstructor
public enum PaymentGroup {

    CARD("CARD"),
    WALLET("WALLET"),
    NET_BANKING("NET_BANKING"),
    UPI("UPI");
    @Analysed
    private final String value;
}
