package in.wynk.payment.dao.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.itunes.ItunesReceiptType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

@Document(collection = "ItunesIdUidMapping")
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@ToString
public class ItunesIdUidMapping implements Serializable {

    private String   uid;
    private int planId;
    private String  itunesId;
    private String receipt;
    private ItunesReceiptType type;

}