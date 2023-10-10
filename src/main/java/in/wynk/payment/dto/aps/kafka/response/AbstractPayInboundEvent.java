package in.wynk.payment.dto.aps.kafka.response;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
public abstract class AbstractPayInboundEvent extends InboundEvent {
    @Override
    public ChannelType getChannel() {
        return ChannelType.PAY;
    }

}
