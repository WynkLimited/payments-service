package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.dto.AbstractPack;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChargingStatusResponse {

    private final String tid;
    private final int planId;
    private final long validity;
    private final AbstractPack packDetails;
    private final TransactionStatus transactionStatus;


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
