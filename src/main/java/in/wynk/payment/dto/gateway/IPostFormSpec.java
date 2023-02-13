package in.wynk.payment.dto.gateway;

import java.util.Map;

public interface IPostFormSpec<T, R> {

    Map<T, R> getForm();

}
