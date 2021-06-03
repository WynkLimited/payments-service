package in.wynk.payment.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SavedDetailsKey implements Serializable {

    @Field("uid")
    private String uid;

    @Field("device_id")
    private String deviceId;

    @Field("payment_code")
    private String paymentCode;

    @Field("payment_group")
    private String paymentGroup;

    @Override
    public int hashCode() {
        return internalHashCode(uid)+internalHashCode(deviceId)+internalHashCode(paymentCode)+internalHashCode(paymentGroup);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SavedDetailsKey) {
            SavedDetailsKey savedDetailsKey = (SavedDetailsKey) o;
            return savedDetailsKey.getUid().equals(this.uid) && (Objects.isNull(savedDetailsKey.getDeviceId()) || savedDetailsKey.getDeviceId().equals(this.deviceId)) && savedDetailsKey.getPaymentCode().equals(this.paymentCode) && savedDetailsKey.getPaymentGroup().equals(this.paymentGroup);
        }
        return false;
    }

    public int internalHashCode(String s) {
        if (StringUtils.isNotBlank(s)) {
            return s.hashCode();
        }
        return 0;
    }

}