package in.wynk.payment.dto.aps.response.status.charge;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.constant.UpiConstants;
import in.wynk.payment.dto.aps.common.RefundInfo;
import in.wynk.payment.dto.aps.common.SiRegistrationStatus;
import in.wynk.payment.dto.aps.request.callback.ApsCallBackRequestPayload;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static in.wynk.payment.dto.aps.common.ApsConstant.DEFAULT_CIRCLE_ID;

@Getter
@Setter
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
    private long paymentStartDate;
    private long paymentEndDate;
    private SiRegistrationStatus mandateStatus;
    private String mandateId;
    private String nextRetry;
    private String redirectionUrl;
    private String paymentRoutedThrough;
    private String upiApp;
    @Builder.Default
    private Integer circleId= DEFAULT_CIRCLE_ID;

    public static ApsChargeStatusResponse[] from (ApsCallBackRequestPayload request) {
        ApsChargeStatusResponse[] responses = new ApsChargeStatusResponse[1];
        responses[0] = ApsChargeStatusResponse.builder()
                .pgId(request.getPgId())
                .pgSystemId(request.getPgSystemId())
                .orderId(request.getOrderId())
                .lob(request.getLob())
                .pgStatus(Objects.nonNull(request.getStatus()) ? "PG_".concat(request.getStatus().toString()) : "UNKNOWN")
                .paymentStatus(Objects.nonNull(request.getStatus()) ? "PAYMENT_".concat(request.getStatus().toString()) : "UNKNOWN")
                .paymentAmount(Objects.nonNull(request.getAmount()) ? request.getAmount().doubleValue() : 0.0)
                .currency(Objects.nonNull(request.getCurrency()) ? request.getCurrency().name() : "INR")
                .vpa(request.getVpa())
                .cardNetwork(request.getCardNetwork())
                .bankCode(UpiConstants.UPI.equals(request.getPaymentMode().toString()) ? request.getUpiFlow() : request.getBankCode())
                .bankRefNo(request.getBankRefId())
                .paymentMode(request.getPaymentMode().name())
                .paymentDate(Objects.nonNull(request.getPaymentDate()) ? Long.toString(request.getPaymentDate()) : new Date().toString())
                .errorCode(request.getErrorCode())
                .errorDescription(request.getErrorMsg())
                .paymentGateway(request.getPg())
                .paymentRoutedThrough(request.getPg())
                .mandateId(request.getMandateId())
                .mandateStatus(request.getMandateStatus())
                .upiApp(request.getUpiApp())
                .merchantId(request.getMid()).build();
        return responses;
    }
}
