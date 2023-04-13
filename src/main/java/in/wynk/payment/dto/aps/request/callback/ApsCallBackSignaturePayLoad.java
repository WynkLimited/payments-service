package in.wynk.payment.dto.aps.request.callback;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.aps.common.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public class ApsCallBackSignaturePayLoad implements Serializable {
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
    private String vpa;
    private String errorCode;
    private String errorMsg;
    private Long timestamp;
    private String mandateId;
    private SiRegistrationStatus mandateStatus;
}
