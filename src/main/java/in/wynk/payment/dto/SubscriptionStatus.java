package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@AnalysedEntity
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionStatus {
    @Analysed
    private int planId;
    @Analysed
    private int productId;

    @Analysed
    private long validity;

    @Analysed
    private String packGroup;
    private String service;
    private String category;

    private boolean combo;
    private boolean active;
    @Setter
    private Long startDate;
    private boolean autoRenew;

}