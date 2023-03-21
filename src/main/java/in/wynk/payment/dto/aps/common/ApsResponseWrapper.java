package in.wynk.payment.dto.aps.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ApsResponseWrapper<T> {
    private T body;
    private String statusCode;
}
