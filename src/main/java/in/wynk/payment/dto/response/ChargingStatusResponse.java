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
    private Long validity;


    public static ChargingStatusResponse success(String tid, Long validity){
        return ChargingStatusResponse.builder().tid(tid).validity(validity).transactionStatus(TransactionStatus.SUCCESS).build();
    }

    public static ChargingStatusResponse failure(String tid){
        return ChargingStatusResponse.builder().tid(tid).transactionStatus(TransactionStatus.FAILURE).build();
    }

    public static ChargingStatusResponse inProgress(String tid){
        return ChargingStatusResponse.builder().tid(tid).transactionStatus(TransactionStatus.INPROGRESS).build();
    }
}
