package in.wynk.payment.dto.response.phonepe;
import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.common.dto.BaseResponse;
import lombok.*;
import lombok.experimental.SuperBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@ToString
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PhonePeWalletResponse extends BaseResponse<PhonePeResponseData> {
    private PhonePeResponseData data;


}
