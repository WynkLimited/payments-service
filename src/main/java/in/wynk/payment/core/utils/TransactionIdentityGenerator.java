package in.wynk.payment.core.utils;

import com.datastax.driver.core.utils.UUIDs;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;

public class TransactionIdentityGenerator implements IdentifierGenerator {

    @Override
    public Serializable generate(SharedSessionContractImplementor sharedSessionContractImplementor, Object o) throws HibernateException {
        return UUIDs.timeBased().toString();
    }

    @Override
    public boolean supportsJdbcBatchInserts() {
        return false;
    }

}
