package in.wynk.payment.dto.gpbs.acknowledge.queue;

import com.github.annotation.analytic.core.annotations.Analysed;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractAcknowledgementMessage {

    @Analysed
    private String packageName;

    @Analysed
    private String service;

    @Analysed
    private String purchaseToken;

    @Analysed
    private String developerPayload;

    @Analysed
    private String skuId;

    @Analysed
    private String paymentCode;

    @Analysed
    private String type;

    @Analysed
    private String txnId;
}
