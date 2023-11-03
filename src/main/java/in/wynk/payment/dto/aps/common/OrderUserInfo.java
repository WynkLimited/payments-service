package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
public class OrderUserInfo extends AbstractUserInfo {
    private String serviceInstance;
}

