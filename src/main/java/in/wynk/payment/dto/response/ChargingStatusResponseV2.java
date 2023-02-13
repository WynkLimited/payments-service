package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.dto.response.presentation.paymentstatus.AbstractPayUPaymentStatus;
import lombok.Getter;
import lombok.experimental.SuperBuilder;


/**
 * @author Nishesh Pandey
 */
@SuperBuilder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChargingStatusResponseV2 extends AbstractPayUPaymentStatus {
    private final long validity;

    public static ChargingStatusResponseV2 success (String tid, Long validity, int planId) {
        return ChargingStatusResponseV2.builder().tid(tid).validity(validity).planId(planId).transactionStatus(TransactionStatus.SUCCESS).build();
    }

    public static ChargingStatusResponseV2 failure (String tid, int planId) {
        return ChargingStatusResponseV2.builder().tid(tid).planId(planId).transactionStatus(TransactionStatus.FAILURE).build();
    }

    public static ChargingStatusResponseV2 inProgress (String tid) {
        return ChargingStatusResponseV2.builder().tid(tid).transactionStatus(TransactionStatus.INPROGRESS).build();
    }
}
