package in.wynk.payment.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

@Deprecated
@Document(collection = "ItunesIdUidMapping")
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@ToString
public class ItunesIdUidMapping implements Serializable {

    @Id
    private String  itunesId;
    private String   uid;
    private int planId;
    private String receipt;
    private String type;
    private String msisdn;

}