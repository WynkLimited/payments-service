package in.wynk.payment.dto.aps.kafka.response;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public abstract class InboundEvent {

    private String to;
    private String from;
    @Setter
    private String orgId;
    @Setter
    private String serviceId;
    private String sessionId;

    public abstract IMessageType getType();

    public abstract ChannelType getChannel();
}
