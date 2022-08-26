package in.wynk.payment.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class BaseTDRResponse {

    private final double tdr;

    public static BaseTDRResponse from(double tdr) {
        return new BaseTDRResponse(tdr);
    }

}
