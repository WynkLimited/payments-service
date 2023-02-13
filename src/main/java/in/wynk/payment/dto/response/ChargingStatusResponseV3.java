package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.dto.response.presentation.paymentstatus.AbstractAirtelPaymentStatus;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@SuperBuilder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChargingStatusResponseV3 extends AbstractAirtelPaymentStatus {

    private final long validity;

    public static ChargingStatusResponseV3 success (String tid, Long validity, int planId) {
        return ChargingStatusResponseV3.builder().tid(tid).validity(validity).planId(planId).transactionStatus(TransactionStatus.SUCCESS).build();
    }

    public static ChargingStatusResponseV3 failure (String tid, int planId) {
        return ChargingStatusResponseV3.builder().tid(tid).planId(planId).transactionStatus(TransactionStatus.FAILURE).build();
    }

    public static ChargingStatusResponseV3 inProgress (String tid) {
        return ChargingStatusResponseV3.builder().tid(tid).transactionStatus(TransactionStatus.INPROGRESS).build();
    }
}
