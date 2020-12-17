package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.common.enums.TransactionStatus;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChargingStatusResponse {
    private TransactionStatus transactionStatus;
    private String tid;
    private int planId;
    private Long validity;


    public static ChargingStatusResponse success(String tid, Long validity, int planId){
        return ChargingStatusResponse.builder().tid(tid).validity(validity).planId(planId).transactionStatus(TransactionStatus.SUCCESS).build();
    }

    public static ChargingStatusResponse failure(String tid, int planId){
        return ChargingStatusResponse.builder().tid(tid).transactionStatus(TransactionStatus.FAILURE).build();
    }

    public static ChargingStatusResponse inProgress(String tid){
        return ChargingStatusResponse.builder().tid(tid).transactionStatus(TransactionStatus.INPROGRESS).build();
    }
}
