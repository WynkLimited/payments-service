package in.wynk.payment.dto.aps.response.status.charge;

import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.aps.common.RefundInfo;
import in.wynk.payment.dto.aps.request.callback.ApsCallBackRequestPayload;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@SuperBuilder
@ToString
@NoArgsConstructor
public class ApsChargeStatusResponse implements Serializable {
    private String pgId;
    private String pgSystemId;
    private String orderId;
    private String pgStatus;
    private String paymentStatus;
    private BigDecimal paymentAmount;
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

    public static ApsChargeStatusResponse[] from (ApsCallBackRequestPayload request, String bankCode) { // here we will not get Bank code
        ApsChargeStatusResponse[] responses = new ApsChargeStatusResponse[1];
        responses[0] = ApsChargeStatusResponse.builder().pgId(request.getPgId()).pgSystemId(request.getPgSystemId()).orderId(request.getOrderId())
                .pgStatus("PG_".concat(request.getStatus().getValue()))
                .paymentStatus("PAYMENT_".concat(request.getStatus().getValue()))
                .paymentAmount(request.getAmount())
                .currency(request.getCurrency().name())
                .bankCode(bankCode)
                .bankRefNo(request.getBankRefId())
                .paymentMode(request.getPaymentMode().name())
                .paymentDate(Long.toString(request.getTimestamp()))
                .errorCode(request.getErrorCode())
                .errorDescription(request.getErrorMsg())
                .paymentGateway(request.getPg())
                .merchantId(request.getMid()).build();
        return responses;
    }
}
