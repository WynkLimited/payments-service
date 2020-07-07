package in.wynk.payment.dto.response.paytm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@AllArgsConstructor
@RequiredArgsConstructor
public class PaytmChargingStatusResponse {
    @JsonProperty("MID")
    private String mid;

    @JsonProperty("TXNID")
    private String txnId;

    @JsonProperty("ORDERID")
    private String orderId;

    @JsonProperty("BANKTXNID")
    private String bankTxnId;

    @JsonProperty("TXNAMOUNT")
    private String txnAmount;

    @JsonProperty("STATUS")
    private String status;

    @JsonProperty("RESPCODE")
    private String responseCode;

    @JsonProperty("RESPMSG")
    private String respMsg;

    @JsonProperty("TXNDATE")
    private String txnDate;

    @JsonProperty("GATEWAYNAME")
    private String gatewayName;

    @JsonProperty("BANKNAME")
    private String bankName;

    @JsonProperty("PAYMENTMODE")
    private String paymentMode;

    @JsonProperty("TXNTYPE")
    private String txnType;

    @JsonProperty("REFUNDAMT")
    private String refundAmt;

}
