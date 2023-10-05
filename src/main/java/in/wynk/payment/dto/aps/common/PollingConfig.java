package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@ToString
@NoArgsConstructor
public class PollingConfig {
    private long start;
    private long end;
    private long interval;
}
