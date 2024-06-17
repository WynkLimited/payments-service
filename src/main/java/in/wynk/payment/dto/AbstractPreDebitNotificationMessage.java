package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractPreDebitNotificationMessage {
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
