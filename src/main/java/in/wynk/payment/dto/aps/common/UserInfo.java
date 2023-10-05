package in.wynk.payment.dto.aps.common;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
public class UserInfo {
    private String emailId;
    private String communicationNo;
    private String salutation;
    private String firstName;
    private String middleName;
    private String lastName;
    private String loginId;
}
