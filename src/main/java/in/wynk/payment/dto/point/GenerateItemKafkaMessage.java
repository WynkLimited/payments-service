package in.wynk.payment.dto.point;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.dto.GenerateItemEvent;
import in.wynk.stream.advice.KafkaEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Calendar;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@RequiredArgsConstructor
public class GenerateItemKafkaMessage {
    private String transactionId;
    private String itemId;
    private String uid;
    @Temporal(TemporalType.TIMESTAMP)
    private Calendar createdDate;
    @Temporal(TemporalType.TIMESTAMP)
    private Calendar updatedDate;
    private TransactionStatus transactionStatus;
    private PaymentEvent event;

    public static GenerateItemKafkaMessage from (GenerateItemEvent event) {
        return GenerateItemKafkaMessage.builder()
                .transactionId(event.getTransactionId())
                .itemId(event.getItemId())
                .uid(event.getUid())
                .createdDate(event.getCreatedDate())
                .updatedDate(event.getUpdatedDate())
                .transactionStatus(event.getTransactionStatus())
                .event(event.getEvent())
                .build();
    }
}

