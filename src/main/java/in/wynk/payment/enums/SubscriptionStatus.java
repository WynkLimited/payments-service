package in.wynk.payment.enums;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public enum SubscriptionStatus {
    ACTIVE,
    PRERENEWAL,
    GRACE,
    SUSPENDED,
    DEACTIVATED,
    NEVER_SUBSCRIBED,
    IN_PROGRESS,
    ONHOLD,
    UNKNOWN;

    public static final Set<SubscriptionStatus> PERSISTABLE_STATUS =
            new HashSet<>(Arrays.asList(ONHOLD));

    public static SubscriptionStatus getStatusFromString(String s) {
        if (StringUtils.isBlank(s)) {
            return null;
        }

        for (SubscriptionStatus status : values()) {
            if (s.equalsIgnoreCase(status.name())) {
                return status;
            }
        }
        return null;
    }
}
