package in.wynk.payment.dto.response.paytm;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaytmChargingStatusResponse {

    private PaytmResponseHead head;
    private Body body;

    @Getter
    @NoArgsConstructor
    private static class Body {

        private PaytmResultInfo resultInfo;
        private String txnId;
        private String bankTxnId;
        private String orderId;
        private String txnAmount;
        private String txnType;
        private String gatewayName;
        private String bankName;
        private String mid;
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

    public String getStatus() {
        return getBody().getResultInfo().getResultStatus();
    }

}
