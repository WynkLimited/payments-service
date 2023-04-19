package in.wynk.payment.presentation.dto.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SuccessPaymentStatusResponse extends PaymentStatusResponse {

    private final long validity;
    private final String title;
    private final String subtitle;
    private final String buttonText;

    public static ChargingStatusResponse success(String tid, Long validity, int planId) {
        return ChargingStatusResponse.builder().tid(tid).validity(validity).planId(planId).transactionStatus(TransactionStatus.SUCCESS).build();
    }

    public static ChargingStatusResponse failure(String tid, int planId) {
        return ChargingStatusResponse.builder().tid(tid).planId(planId).transactionStatus(TransactionStatus.FAILURE).build();
    }

    public static ChargingStatusResponse inProgress(String tid){
        return ChargingStatusResponse.builder().tid(tid).transactionStatus(TransactionStatus.INPROGRESS).build();
    }

}
