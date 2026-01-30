package in.wynk.payment.core.dao.entity;

import com.github.annotation.analytic.core.annotations.Analysed;

import java.io.Serializable;

public interface IUserDetails extends Serializable {

    @Analysed(name = "userType")
    String getType();

    String getSi();

    String getDslId();

    String getMsisdn();

    String getCountryCode();

    String getSubscriberId();

}