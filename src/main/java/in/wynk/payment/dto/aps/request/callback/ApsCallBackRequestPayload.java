package in.wynk.payment.dto.aps.request.callback;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.dto.ChecksumHeaderCallbackRequest;
import in.wynk.payment.dto.aps.common.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.http.HttpHeaders;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApsCallBackRequestPayload extends ChecksumHeaderCallbackRequest<ApsCallBackRequestPayload> implements Serializable {
    private static final long serialVersionUID = 7427670413183914778L;
    private String pgId;
    private String orderId;
    private String pgSystemId;
    private String bankRefId;
    private String mid;
    private WebhookConfigType type;
    private String lob;
    private WebhookEventStatus status;
    private BigDecimal amount;
    private PaymentMode paymentMode;
    private Currency currency;
    private String pg;
    private String bankCode;
    private String bankName;
    private String vpa;
    private String errorCode;
    private String errorMsg;
    private Long timestamp;
    private String mandateId;
    private String upiFlow;
    private String upiApp;
    private String cardNetwork;
    private Long paymentDate;
    @JsonIgnore
    private String checksum;
    private SiRegistrationStatus mandateStatus;
    //parameters for redirection callback
    private String paymentStatus;
    private String start;
    private String end;
    private String interval;
    private String redirectionDestination;
    @JsonProperty("Signature")
    private String signature;

    @JsonIgnore
    public String getTransactionId() {
        return this.getOrderId();
    }

    @Override
    @JsonIgnore
    public ApsCallBackRequestPayload withHeader(HttpHeaders headers) {
        this.checksum = headers.getFirst(ApsConstant.SIGNATURE);
        return this;
    }
}
