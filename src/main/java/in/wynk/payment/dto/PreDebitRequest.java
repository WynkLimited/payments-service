package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Calendar;
import java.util.Date;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
public class PreDebitRequest {
    @Analysed
    private String transactionId;

    @Analysed
    private String initialTransactionId;

    @Analysed
    private String lastSuccessTransactionId;

    /*@Analysed
    private String date;*/

    @Analysed
    private String uid;

    @Analysed
    private Integer planId;

    @Analysed
    private String paymentCode;

    @Temporal(TemporalType.DATE)
    @Analysed
    private Calendar day;

    @Temporal(TemporalType.TIME)
    @Analysed
    private Date hour;
}
