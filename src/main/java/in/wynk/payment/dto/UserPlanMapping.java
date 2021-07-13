package in.wynk.payment.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
public class UserPlanMapping<T> {
    @Builder.Default
    @Setter
    private int planId = -1;
    private final String uid;
    private final String msisdn;
    @Setter
    private T message;
}
