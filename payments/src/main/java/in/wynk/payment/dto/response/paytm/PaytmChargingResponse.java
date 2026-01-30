package in.wynk.payment.dto.response.paytm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

@Getter
@ToString
@RequiredArgsConstructor
@AllArgsConstructor
public class PaytmChargingResponse {
    @JsonProperty("TxnId")
    private String txnId;

    @JsonProperty("MID")
    private String mid;

    @JsonProperty("OrderId")
    private String orderId;

    @JsonProperty("TxnAmount")
    private BigDecimal txnAmount;

    @JsonProperty("BankTxnId")
    private String bankTxnId;

    @JsonProperty("ResponseCode")
    @Setter
    private String responseCode;

    @JsonProperty("ResponseMessage")
    private String responseMessage;

    @JsonProperty("Status")
    private String status;

    @JsonProperty("PaymentMode")
    private String paymentMode;

    @JsonProperty("BankName")
    private String bankName;

    @JsonProperty("CustId")
    private String custId;

    @JsonProperty("MBID")
    private String mbid;

    @JsonProperty("CheckSum")
    private String checkSum;

}
