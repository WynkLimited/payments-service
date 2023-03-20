package in.wynk.payment.dto.aps.common;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@Builder
@ToString
public class ApsResponseBody<T> {
    private T data;
    private String requestId;
    private boolean result;
}
