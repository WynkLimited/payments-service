package in.wynk.payment.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.io.Serializable;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class IdUidMapping implements Serializable {
}
