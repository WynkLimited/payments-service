package in.wynk.payment.dto;

public interface IPurchaseDetails {
    IAppDetails getAppDetails();
    IUserDetails getUserDetails();
    PaymentDetails getPaymentDetails();
    IProductDetails getProductDetails();
}
