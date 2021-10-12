package in.wynk.payment.dto.response.addtobill;

import lombok.*;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EligibilityResponseBody {
    private  String channel;
    private  String si;
    private  List<EligibleServices> serviceList;
}
