package in.wynk.payment.dto.aps.common;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class UserInfo {
    /**
    * LoginID where KCI being sent
    */
    private String loginId;
}
