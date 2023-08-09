package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AnalysedEntity
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionStatus {
    @Analysed
    private int planId;
    @Analysed
    private final int productId;

    @Analysed
    private long validity;

    @Analysed
    private final String packGroup;
    private final String service;
    private final String category;

    private boolean combo;
    private final boolean active;
    @Setter
    private Long startDate;
    private boolean autoRenew;

}