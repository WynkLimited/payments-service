package in.wynk.payment.dto.aps.request.delete;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
public class DeleteVpaRequest {
    private List<String> vpaIds;
}
