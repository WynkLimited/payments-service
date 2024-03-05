package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.core.dao.entity.ISessionDetails;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class SessionDetails implements ISessionDetails {

    @JsonProperty("sid")
    @Analysed
    private String sessionId;
}
