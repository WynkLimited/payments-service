package in.wynk.payment.dto.response.addtobill;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class EligibilityResponseBody {
    private String channel;
    private String si;
    private List<EligibleServices> serviceList;
}
