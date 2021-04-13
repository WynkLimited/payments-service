package in.wynk.payment.dto.phonepe;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceContext {
    private long phonePeVersionCode;
}
