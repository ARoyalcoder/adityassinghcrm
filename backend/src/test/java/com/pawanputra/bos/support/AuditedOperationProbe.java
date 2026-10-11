package com.pawanputra.bos.support;

import com.pawanputra.bos.audit.api.AuditAction;
import com.pawanputra.bos.audit.api.AuditedOperation;
import org.springframework.stereotype.Component;

/** Stands in for a future module's service, to prove {@code @AuditedOperation} needs nothing but the annotation. */
@Component
public class AuditedOperationProbe {

    @AuditedOperation(action = AuditAction.EXPORT, module = "probe", entity = "Customer", summary = "Exported customers")
    public int exportCustomers(String format, int rowCount) {
        return rowCount;
    }

    @AuditedOperation(action = AuditAction.IMPORT, module = "probe", summary = "Imported price list")
    public void failingImport(String fileName) {
        throw new IllegalStateException("file is corrupt");
    }
}
