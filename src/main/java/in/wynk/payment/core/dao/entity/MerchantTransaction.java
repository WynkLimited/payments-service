package in.wynk.payment.core.dao.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import com.vladmihalcea.hibernate.type.json.JsonStringType;
import in.wynk.audit.entity.AuditableEntity;
import in.wynk.common.constant.BaseConstants;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;

import java.util.Iterator;

import static in.wynk.logging.utils.LogbackUtil.MAPPER;
@Slf4j
@Entity
@Getter
@Setter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "merchant_transaction")
@TypeDef(name = "json", typeClass = JsonStringType.class)
@NamedQueries({
        @NamedQuery(name = "MerchantTransaction.findPartnerReferenceById", query = "SELECT m.externalTransactionId FROM MerchantTransaction m WHERE m.id=:txnId", hints = @QueryHint(name = "javax.persistence.query.timeout", value = "300")),
        @NamedQuery(name = "MerchantTransaction.findTransactionIdByOrderId", query = "SELECT m.id FROM MerchantTransaction m WHERE m.orderId=:orderId", hints = @QueryHint(name = "javax.persistence.query.timeout", value = "300"))
})
public class MerchantTransaction extends AuditableEntity {

    @Id
    @Analysed(name = BaseConstants.TRANSACTION_ID)
    @Column(name = "transaction_id")
    @Setter(AccessLevel.NONE)
    private String id;

    @Analysed
    @Column(name = "order_id")
    private String orderId;

    @Analysed
    @Column(name = "external_token_reference_id")
    private String externalTokenReferenceId;

    @Analysed
    @Column(name = "merchant_transaction_reference_id")
    private String externalTransactionId;

    @Type(type = "json")
    @Analysed
    @Column(name = "merchant_request", nullable = false, columnDefinition = "json")
    private Object request;

    @Type(type = "json")
    @Analysed
    @Column(name = "merchant_response", columnDefinition = "json")
    private Object response;

    public <T> T getRequest() {
        return (T) request;
    }

    public <T> T getResponse() {
        return (T) response;
    }

    public String getPaymentMode() {
        return extractField("paymentMode");
    }

    public String getErrorCode() {
        return extractField("errorCode");
    }

    public String getErrorReason() {
        return extractField("errorDescription");
    }

    private String extractField(String key) {
        if (response == null) return null;

        try {
            // CASE 1: response stored as String
            if (response instanceof String) {
                JsonNode root = MAPPER.readTree((String) response);
                return extractFromJsonNode(root, key);
            }

            // CASE 2: response stored as List (ARRAY case)
            if (response instanceof Iterable) {
                for (Object obj : (Iterable<?>) response) {
                    JsonNode node = MAPPER.valueToTree(obj);
                    JsonNode value = node.get(key);
                    if (value != null && !value.isNull()) {
                        return value.asText();
                    }
                }
                return null;
            }

            // CASE 3: response stored as Map (OBJECT case)
            JsonNode root = MAPPER.valueToTree(response);
            return extractFromJsonNode(root, key);

        } catch (Exception e) {
            log.warn("Failed to parse merchant_response for txnId={}", id, e);
            return null;
        }
    }

    private String extractFromJsonNode(JsonNode root, String key) {
        if (root.isArray() && root.size() > 0) {
            JsonNode value = root.get(0).get(key);
            if (value != null && !value.isNull()) return value.asText();
        } else if (root.isObject()) {
            JsonNode value = root.get(key);
            if (value != null && !value.isNull()) return value.asText();
        }
        return null;
    }

}
