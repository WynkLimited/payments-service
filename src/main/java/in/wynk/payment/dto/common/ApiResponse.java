package in.wynk.payment.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class ApiResponse {
    private boolean result;
    private String requestId;
}
