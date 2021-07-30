package in.wynk.payment.dto.phonepe;

import in.wynk.common.dto.BaseResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PhonePeResponse<T extends PhonePeResponse.PhonePeResponseWrapper> extends BaseResponse<T, Void> {

    private T data;
    private PhonePeStatusEnum code;

    public interface PhonePeResponseWrapper { }

}
