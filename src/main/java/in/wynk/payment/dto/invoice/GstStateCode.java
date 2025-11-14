package in.wynk.payment.dto.invoice;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
public class GstStateCode {
    private String optimusGstStateCode;
    private String geoLocationGstStateCode;
    private String defaultGstStateCode;
}