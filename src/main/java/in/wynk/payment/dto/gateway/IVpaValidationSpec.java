package in.wynk.payment.dto.gateway;

public interface IVpaValidationSpec {

    /**
     *  whether vpa is valid
     */
    boolean isValid();

    /**
    *  vpa of the user
    */
    String getVpa();

    /**
     *  account name associated with vpa
     */
    String getPayerAccountName();

}
