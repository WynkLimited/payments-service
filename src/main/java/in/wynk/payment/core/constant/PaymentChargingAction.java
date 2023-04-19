package in.wynk.payment.core.constant;

public enum PaymentChargingAction {
    HTML("HTML"), ROUTE("ROUTE"), REDIRECT("REDIRECT"), IN_APP("IN_APP"), INTENT("INTENT"), KEY_VALUE("KEY_VALUE"), OTP_LESS("OTP_LESS");

    private final String action;

    PaymentChargingAction (String action) {
        this.action = action;
    }

    public String getAction () {
        return this.action;
    }
}