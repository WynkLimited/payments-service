package in .wynk.payment.core.dao.entity;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Document(collection = "testing_bypass_numbers")
public class TestingByPassNumbers {
    @Id
    private String msisdn;
}