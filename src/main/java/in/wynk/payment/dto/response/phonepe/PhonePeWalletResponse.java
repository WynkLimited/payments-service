package in.wynk.payment.dto.response.phonepe;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.common.dto.BaseResponse;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PhonePeWalletResponse extends BaseResponse<PhonePeResponseData, Void> {

    private String code;
    private PhonePeResponseData data;

}