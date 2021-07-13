package in.wynk.payment.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DecodedNotificationWrapper<R extends IAPNotification> {

    private final boolean eligible;
    private final R decodedNotification;
}
