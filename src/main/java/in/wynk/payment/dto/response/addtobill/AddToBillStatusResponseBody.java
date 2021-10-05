package in.wynk.payment.dto.response.addtobill;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class AddToBillStatusResponseBody {
    private String si;
    private List<AddToBillOrder> ordersList;
}
