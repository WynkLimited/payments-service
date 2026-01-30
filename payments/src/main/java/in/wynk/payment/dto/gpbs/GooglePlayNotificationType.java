package in.wynk.payment.dto.gpbs;

/**
 * @author Nishesh Pandey
 */
public enum GooglePlayNotificationType {
   SUBSCRIPTION_RECOVERED(1), SUBSCRIPTION_RENEWED(2), SUBSCRIPTION_CANCELED(3),
    SUBSCRIPTION_PURCHASED(4), SUBSCRIPTION_ON_HOLD(5), SUBSCRIPTION_IN_GRACE_PERIOD(6),
    SUBSCRIPTION_RESTARTED(7), SUBSCRIPTION_PRICE_CHANGE_CONFIRMED(8),
    SUBSCRIPTION_DEFERRED(9), SUBSCRIPTION_PAUSED(10),
    SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED(11), SUBSCRIPTION_REVOKED(12),
    SUBSCRIPTION_EXPIRED(13);

    private final Integer notificationTpe;

    GooglePlayNotificationType (Integer notificationTpe) {
        this.notificationTpe = notificationTpe;
    }

    public Integer getNotificationTpe () {
        return notificationTpe;
    }


}
