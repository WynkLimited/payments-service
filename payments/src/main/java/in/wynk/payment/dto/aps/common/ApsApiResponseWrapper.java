package in.wynk.payment.dto.aps.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Builder
@ToString
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ApsApiResponseWrapper<T> {
    private T data;
    private String requestId;
    private boolean result;
    private String errorCode;
    private String errorMessage;
}
