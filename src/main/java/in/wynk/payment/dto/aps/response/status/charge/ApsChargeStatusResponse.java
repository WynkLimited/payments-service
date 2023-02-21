package in.wynk.payment.dto.aps.response.status.charge;

import in.wynk.payment.dto.aps.common.RefundInfo;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@SuperBuilder
@ToString
public class ApsChargeStatusResponse {
    private String pgId;
    private String orderId;
    private String pgStatus;
    private String paymentStatus;
    private double paymentAmount;
    private String currency;
    private String bankCode;
    private String bankName;
    private String paymentMode;
    private List<RefundInfo> refundDetails;
    private String paymentDate;
    private String errorCode;
    private String errorDescription;
    private String paymentGateway;
    private String merchantId;
    private String bankRefNo;
    private String cardNetwork;
}
