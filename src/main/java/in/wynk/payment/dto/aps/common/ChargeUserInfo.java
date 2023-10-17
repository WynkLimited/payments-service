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
public class ChargeUserInfo extends AbstractUserInfo {
    /**
     * LoginID where KCI being sent
     */
    private String loginId;
}
