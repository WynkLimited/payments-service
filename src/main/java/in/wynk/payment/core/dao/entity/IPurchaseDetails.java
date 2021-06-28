package in.wynk.payment.core.dao.entity;

public interface IPurchaseDetails {
    IAppDetails getAppDetails();
    IUserDetails getUserDetails();
    IPaymentDetails getPaymentDetails();
    IProductDetails getProductDetails();
}
