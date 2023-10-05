package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
@NoArgsConstructor
public class UserInfo {
    private String emailId;
    private String communicationNo;
    private String salutation;
    private String firstName;
    private String middleName;
    private String lastName;
    private String loginId;
}
