package in.wynk.payment.dto.common;

import java.util.Map;

public interface IPostFormSpec<T, R> {

    Map<T, R> getForm();

}
