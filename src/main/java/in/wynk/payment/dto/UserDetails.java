package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.IUserDetails;
import lombok.*;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

import static in.wynk.common.constant.CacheBeanNameConstants.*;

@Getter
@Builder
@ToString
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class UserDetails implements IUserDetails, Serializable {

    @NotNull
    @Analysed
    @Pattern(regexp = MSISDN_REGEX, message = INVALID_VALUE)
    private String msisdn;

    @Analysed
    @Pattern(regexp = SUBSCRIBER_ID_REGEX, message = INVALID_VALUE)
    private String subscriberId;

    @Analysed
    private String dslId;

    @Analysed
    private String countryCode;

}