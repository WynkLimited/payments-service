package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class WhatsappSessionDetails extends AbstractSessionDetails {
    private String to;
    private String from;
    private String orgId;
    private String serviceId;
    private String requestId;
    private String campaignId;
}
