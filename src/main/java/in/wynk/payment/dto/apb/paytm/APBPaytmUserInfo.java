package in.wynk.payment.dto.apb.paytm;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class APBPaytmUserInfo {
    private String circleId;
    private String loginId;
}
