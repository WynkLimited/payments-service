package in.wynk.payment.dto.aps.response.status.charge;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.aps.common.RefundInfo;
import in.wynk.payment.dto.aps.common.SiRegistrationStatus;
import in.wynk.payment.dto.aps.request.callback.ApsCallBackRequestPayload;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.List;

@Getter
@SuperBuilder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class ApsChargeStatusResponse implements Serializable {
    private String pgId;
    private String pgSystemId;
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
    private String vpa;
    private String merchantId;
    private String bankRefNo;
    private String cardNetwork;
    private String cardRefNo;
    private String cardBin;
    private String lastDigits;
    private String paymentFrequency;
    private String lob;
    private String mid;
    private Integer circleId;
    private long paymentStartDate;
    private long paymentEndDate;
    private SiRegistrationStatus mandateStatus;
    private String mandateId;
    private String nextRetry;
    private String redirectionUrl;
    private String paymentRoutedThrough;
    private String upiApp;

    public static ApsChargeStatusResponse[] from (ApsCallBackRequestPayload request) {
        ApsChargeStatusResponse[] responses = new ApsChargeStatusResponse[1];
        responses[0] = ApsChargeStatusResponse.builder().pgId(request.getPgId()).pgSystemId(request.getPgSystemId()).orderId(request.getOrderId())
                .pgStatus("PG_".concat(request.getStatus().toString()))
                .paymentStatus("PAYMENT_".concat(request.getStatus().toString()))
                .paymentAmount(request.getAmount().doubleValue())
                .currency(request.getCurrency().name())
                .vpa(request.getVpa())
                .cardNetwork(request.getCardNetwork())
                .bankCode(request.getPaymentMode().toString().equals("UPI") ? request.getUpiFlow() : request.getBankCode())
                .bankRefNo(request.getBankRefId())
                .paymentMode(request.getPaymentMode().name())
                .paymentDate(Long.toString(request.getPaymentDate()))
                .errorCode(request.getErrorCode())
                .errorDescription(request.getErrorMsg())
                .paymentGateway(request.getPg())
                .mandateId(request.getMandateId())
                .mandateStatus(request.getMandateStatus())
                .upiApp(request.getUpiApp())
                .merchantId(request.getMid()).build();
        return responses;
    }
}
