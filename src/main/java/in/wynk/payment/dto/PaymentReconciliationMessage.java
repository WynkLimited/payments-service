package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.queue.dto.QueueType;
import in.wynk.queue.dto.WynkQueue;
import in.wynk.session.context.SessionContextHolder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static in.wynk.commons.constants.BaseConstants.CLIENT_ID;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@WynkQueue(queueName = "${payment.pooling.queue.reconciliation.name}", delaySeconds = "${payment.pooling.queue.reconciliation.sqs.producer.delayInSecond}", queueType = QueueType.STANDARD)
@AnalysedEntity
public class PaymentReconciliationMessage extends AbstractTransactionMessage {

    @Analysed
    private String clientId;

    public PaymentReconciliationMessage(Transaction transaction) {
        this.clientId = SessionContextHolder.<SessionDTO>getBody().get(CLIENT_ID);
        super.setUid(transaction.getUid());
        super.setMsisdn(transaction.getMsisdn());
        super.setPaymentCode(transaction.getPaymentChannel());
        super.setPlanId(transaction.getPlanId());
        super.setTransactionId(transaction.getIdStr());
        super.setTransactionEvent(transaction.getType());
    }
}
