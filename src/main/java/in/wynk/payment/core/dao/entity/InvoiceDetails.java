package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;
import java.util.Map;

@Getter
@SuperBuilder
@Document("invoice_details")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class InvoiceDetails extends MongoBaseEntity<String> {

    @Field("gst_percentage")
    private double gstPercentage;
    @Field("default_gst_state_code")
    private String defaultGSTStateCode;
    @Field("lob")
    private String lob;
    @Field("retry_in_seconds")
    private List<Long> retries;
    private String category;

    @Field("supplier_info")
    private SupplierInfo supplierInfo;

    @Field("sac_code")
    private String SACCode;

    @Field("contact_info")
    private String contactInfo;

    @Field("registered_office")
    private String registeredOffice;

    @Field("other_info")
    private String otherInfo;

    @Field("corporate_identity_number")
    private String corporateIdentityNumber;

    @Field("gst_registration_no")
    private String GSTRegistrationNo;
    private String pan;
    private String note;

    @Field("payment_modes")
    private Map<String, String> paymentModes;
}
