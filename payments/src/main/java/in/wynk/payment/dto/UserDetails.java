package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.IUserDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang.StringUtils;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

import static in.wynk.common.constant.BaseConstants.DEFAULT_COUNTRY_CODE;
import static in.wynk.common.constant.CacheBeanNameConstants.*;

@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
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

    private String countryCode;

    @Analysed
    private String si;

    @Override
    public String getCountryCode() {
        return StringUtils.isNotBlank(this.countryCode) ? this.countryCode : DEFAULT_COUNTRY_CODE;
    }

    @Override
    public String getType() {
        return UserDetails.class.getName();
    }

    public in.wynk.common.dto.UserDetails toUserDetails(String uid) {
        return in.wynk.common.dto.UserDetails.builder().uid(uid).msisdn(msisdn).subscriberId(subscriberId).dslId(dslId).subscriberId(si).build();
    }

}