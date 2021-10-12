package in.wynk.payment.core.dao.entity;

import com.github.annotation.analytic.core.annotations.Analysed;

public interface IUserDetails {

    String getDslId();

    String getSi();

    String getMsisdn();

    String getCountryCode();

    String getSubscriberId();

    @Analysed(name = "userType")
    String getType();

}