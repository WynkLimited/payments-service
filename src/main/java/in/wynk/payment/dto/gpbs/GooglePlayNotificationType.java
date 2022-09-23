package in.wynk.payment.dto.gpbs;

/**
 * @author Nishesh Pandey
 */
public enum GooglePlayNotificationType {
    ONE("SUBSCRIPTION_RECOVERED"), TWO("SUBSCRIPTION_RENEWED"), THREE("SUBSCRIPTION_CANCELED"),
    FOUR("SUBSCRIPTION_PURCHASED"), FIVE("SUBSCRIPTION_ON_HOLD"), SIX("SUBSCRIPTION_IN_GRACE_PERIOD"),
    SEVEN("SUBSCRIPTION_RESTARTED"), EIGHT("SUBSCRIPTION_PRICE_CHANGE_CONFIRMED"),
    NINE("SUBSCRIPTION_DEFERRED"), TEN("SUBSCRIPTION_PAUSED"),
    ELEVEN("SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED"), TWELVE("SUBSCRIPTION_REVOKED"),
    THIRTEEN("SUBSCRIPTION_EXPIRED");

    private String notificationTpe;

    GooglePlayNotificationType (String notificationTpe) {
        this.notificationTpe = notificationTpe;
    }

    public String getNotificationTpe () {
        return notificationTpe;
    }


}
