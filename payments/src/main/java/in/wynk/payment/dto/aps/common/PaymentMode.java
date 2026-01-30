package in.wynk.payment.dto.aps.common;

/**
 * @author Nishesh Pandey
 */
public enum PaymentMode {
    NET_BANKING("NB", "Net Banking"),
    CREDIT_CARD("CC", "Credit Card"),
    DEBIT_CARD("DC", "Debit Card"),
    UPI("U", "Upi"),
    WALLET("W", "Wallet"),
    QR_CODE("QR", "QR Code"),
    BNPL("BNPL", "Buy Now Pay Later"),
    COUPON("COUPON", "COUPON"); // Dummy Payment Mode for zero amount payments

    private String paymentModeCode;
    private String optionName;

    PaymentMode(String paymentModeCode, String optionName) {
        this.paymentModeCode = paymentModeCode;
        this.optionName = optionName;
    }
}
