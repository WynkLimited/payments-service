package in.wynk.payment.dto.response.paytm;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaytmChargingStatusResponseBody extends PaytmTransactionalResponseBody {

    private String bankTxnId;
    private String txnAmount;
    private String txnType;
    private String gatewayName;
    private String bankName;
    private String paymentMode;
    private String refundAmt;
    private String txnDate;
    private String subsId;
    private String payableAmount;
    private String paymentPromoCheckoutData;
    private Object vanInfo;
    private Object sourceAccountDetails;
    private String transferMode;
    private String utr;
    private String bankTransactionDate;
    private String rrnCode;
    private String authCode;
    private String merchantUniqueReference;
    private String cardScheme;
    private String bin;
    private String lastFourDigit;
    private Object dccPaymentDetail;
    private boolean internationalCardPayment;
    private String baseCurrency;
    private Object feeRateFactors;

}