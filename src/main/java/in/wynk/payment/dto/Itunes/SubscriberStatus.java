package in.wynk.payment.dto.Itunes;

import in.wynk.payment.enums.SubscriptionStatus;

public class SubscriberStatus {

    private long               expireTimestamp;

    private SubscriptionStatus status;

    public long getExpireTimestamp() {
        return expireTimestamp;
    }

    public void setExpireTimestamp(long expireTimestamp) {
        this.expireTimestamp = expireTimestamp;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

}