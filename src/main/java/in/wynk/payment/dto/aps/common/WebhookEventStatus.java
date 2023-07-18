package in.wynk.payment.dto.aps.common;

/**
 * @author Nishesh Pandey
 */
public enum WebhookEventStatus {
    INITIATE("INITIATE"),
    PENDING("PENDING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED");

    public String getValue () {
        return this.value;
    }

    private final String value;

    WebhookEventStatus(String value) {
        this.value = value;
    }

}
