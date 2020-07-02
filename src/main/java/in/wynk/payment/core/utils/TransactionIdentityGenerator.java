package in.wynk.payment.core.utils;

import com.fasterxml.uuid.Generators;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;

public class TransactionIdentityGenerator implements IdentifierGenerator {

    @Override
    public Serializable generate(SharedSessionContractImplementor sharedSessionContractImplementor, Object o) throws HibernateException {
        return Generators.timeBasedGenerator().generate().toString();
    }

    @Override
    public boolean supportsJdbcBatchInserts() {
        return false;
    }

}
