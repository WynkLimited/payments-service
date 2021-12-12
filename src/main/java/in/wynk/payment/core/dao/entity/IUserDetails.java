package in.wynk.payment.core.dao.entity;

import java.io.Serializable;

public interface IUserDetails extends Serializable {

    String getDslId();

    String getMsisdn();

    String getCountryCode();

    String getSubscriberId();

}