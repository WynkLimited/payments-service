package in.wynk.payment.dto.common;

import java.util.Map;

/**
 * @author Nishesh Pandey
 */
public interface IPostFormSpec<T, R> {

    Map<T, R> getForm();

}