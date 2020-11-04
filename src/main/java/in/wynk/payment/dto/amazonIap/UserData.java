package in.wynk.payment.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@AnalysedEntity
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserData {
    @Analysed
    private String userId;
    @Analysed
    private String marketPlace;

}
