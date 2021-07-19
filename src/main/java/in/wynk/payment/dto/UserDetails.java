package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.IUserDetails;
import lombok.*;

import java.io.Serializable;

@Getter
@Builder
@ToString
@AnalysedEntity
@AllArgsConstructor
@NoArgsConstructor
public class UserDetails implements IUserDetails, Serializable  {

    @Analysed
    private String msisdn;
    @Analysed
    private String dslId;
    @Analysed
    private String subscriberId;

}
