package in.wynk.payment.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class BaseTDRResponse {

    private final Double tdr;

    public static BaseTDRResponse from(Double tdr) {
        if (tdr == null) {
            return null;
        }
        return new BaseTDRResponse(tdr);
    }

}
