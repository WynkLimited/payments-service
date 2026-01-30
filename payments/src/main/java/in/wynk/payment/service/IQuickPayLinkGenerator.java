package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IProductDetails;

public interface IQuickPayLinkGenerator {

    String generate(String tid);

    String generate(String tid, String clientAlias, IAppDetails appDetails, IProductDetails productDetails);

    String generate(String tid, String clientAlias, String oldSid, IAppDetails appDetails, IProductDetails productDetails);


}
