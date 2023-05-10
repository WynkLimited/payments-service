package in.wynk.payment.core.dao.entity;

import org.apache.kafka.common.protocol.types.Field;

import java.io.Serializable;

public interface IGeolocation extends Serializable {
    String getCountryCode();

    String getStateCode();
    String getIp();
}
