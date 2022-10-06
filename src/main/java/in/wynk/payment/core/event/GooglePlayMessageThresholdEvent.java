package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.queue.dto.MessageThresholdExceedEvent;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.springframework.http.HttpRequest;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class GooglePlayMessageThresholdEvent extends MessageThresholdExceedEvent {
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
}
