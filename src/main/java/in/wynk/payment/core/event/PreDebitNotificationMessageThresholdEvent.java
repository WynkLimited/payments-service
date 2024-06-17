package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.queue.dto.MessageThresholdExceedEvent;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Calendar;
import java.util.Date;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class PreDebitNotificationMessageThresholdEvent extends MessageThresholdExceedEvent {

    @Analysed
    private String transactionId;

    @Analysed
    private String initialTransactionId;

    @Analysed
    private String lastSuccessTransactionId;

    @Temporal(TemporalType.DATE)
    @Analysed
    private Calendar renewalDay;

    @Temporal(TemporalType.TIME)
    @Analysed
    private Date renewalHour;
}
