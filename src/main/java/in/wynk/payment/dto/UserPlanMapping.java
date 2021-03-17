package in.wynk.payment.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
public class UserPlanMapping {
    @Builder.Default
    @Setter
    private int planId = -1;
    private final String uid;
    private final String msisdn;
    @Setter
    private Object message;

//    private static final UserPlanMapping DEFAULT_INSTANCE = UserPlanMapping.builder().build();
//
//    public static UserPlanMapping defaultMapping(){
//        return DEFAULT_INSTANCE;
//    }
}
