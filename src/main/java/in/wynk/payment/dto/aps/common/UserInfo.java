package in.wynk.payment.dto.aps.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserInfo {
    private String emailId;
    private String communicationNo;//used during order creation request
    private String salutation;
    private String firstName;
    private String middleName;
    private String lastName;
    private String loginId;
    private String communicationNumber;//used during order status response
}
