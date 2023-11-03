package in.wynk.payment.dto.aps.response.status.charge;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.aps.common.RefundInfo;
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
    private MandateStatus mandateStatus;
    private String mandateId;
    private String nextRetry;
    private String redirectionUrl;
    private String paymentRoutedThrough;
}
