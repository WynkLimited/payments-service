package in.wynk.payment.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.core.dto.itunes.ItunesReceiptType;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

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
    private String service;
    private ItunesReceiptType type;
    private String msisdn;

}