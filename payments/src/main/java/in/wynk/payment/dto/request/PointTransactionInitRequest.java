package in.wynk.payment.dto.request;

import in.wynk.common.dto.SessionDTO;
import in.wynk.session.context.SessionContextHolder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Objects;

@Getter
@SuperBuilder
public class PointTransactionInitRequest extends AbstractTransactionInitRequest {

    private String itemId;

    @Override
    public double getAmount () {
        if (super.getAmount() != 0.0) {
            return super.getAmount();
        }
        SessionDTO sessionDto = SessionContextHolder.getBody();
        return Objects.nonNull(sessionDto.get("price")) ? Double.parseDouble(sessionDto.get("price")) : 0.0;
    }
}

