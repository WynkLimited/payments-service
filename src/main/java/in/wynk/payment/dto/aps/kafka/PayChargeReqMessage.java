package in.wynk.payment.dto.aps.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.UserDetails;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import javax.validation.Valid;
import java.io.Serializable;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@AnalysedEntity
@JsonIgnoreProperties(ignoreUnknown = true)
@RequiredArgsConstructor
public class PayChargeReqMessage extends AbstractMessage implements Serializable {
    private String from;
    private String to;
    private String type;
    private String channel;
    private String campaignId;

    @Valid
    @Analysed
    private AppDetails appDetails;

    @Valid
    @Analysed
    private UserDetails userDetails;

    public boolean isMandateSupported () {
        return this.getPaymentDetails().isMandate();
    }

}
