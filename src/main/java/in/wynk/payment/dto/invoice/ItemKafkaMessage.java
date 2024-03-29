package in.wynk.payment.dto.invoice;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.stream.advice.KafkaEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
public abstract class ItemKafkaMessage {
}
