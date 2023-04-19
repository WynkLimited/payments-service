package in.wynk.payment.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PollingConfig {
    private long interval;
    private long frequency;
    private long timeout;
    private String endpoint;
}
