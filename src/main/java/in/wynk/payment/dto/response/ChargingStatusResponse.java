package in.wynk.payment.dto.response;

import in.wynk.commons.enums.TransactionStatus;
import lombok.Builder;

@Builder
public class ChargingStatusResponse {
    TransactionStatus transactionStatus;

    public static ChargingStatusResponse success(){
        return ChargingStatusResponse.builder().transactionStatus(TransactionStatus.SUCCESS).build();
    }

    public static ChargingStatusResponse failure(){
        return ChargingStatusResponse.builder().transactionStatus(TransactionStatus.FAILURE).build();
    }

    public static ChargingStatusResponse inProgress(){
        return ChargingStatusResponse.builder().transactionStatus(TransactionStatus.INPROGRESS).build();
    }
}
