package in.wynk.payment.core.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Key implements Serializable {

    private String uid;
    private String paymentCode;
    private String paymentGroup;

}
