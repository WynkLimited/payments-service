package in.wynk.payment.dto;

import in.wynk.payment.core.dao.entity.IUserDetails;
import lombok.*;

import java.io.Serializable;

@Getter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class UserDetails implements IUserDetails, Serializable  {

    private String msisdn;
    private String dslId;
    private String subscriberId;

}
