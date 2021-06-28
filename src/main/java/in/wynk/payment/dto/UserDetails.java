package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.core.dao.entity.IUserDetails;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Builder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDetails implements IUserDetails, Serializable  {

    private String msisdn;
    private String dslId;
    private String subscriberId;

}
