package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CombinedUserDetails implements ICombinedUserDetails {
    private IAppDetails appDetails;
    private IUserDetails userDetails;
}
