package in.wynk.payment.dto.response.addtobill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AddToBillStatusResponseBody {
    private String si;
    private List<AddToBillOrder> ordersList;
}
