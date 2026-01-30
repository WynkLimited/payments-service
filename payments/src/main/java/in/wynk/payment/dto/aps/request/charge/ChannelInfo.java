package in.wynk.payment.dto.aps.request.charge;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class ChannelInfo {
    private String redirectionUrl;
}
