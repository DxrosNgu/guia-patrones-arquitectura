package com.mercadoinventos.inventory;

import io.smallrye.faulttolerance.api.BeforeRetryHandler;
import jakarta.enterprise.context.Dependent;
import org.eclipse.microprofile.faulttolerance.ExecutionContext;
import org.jboss.logging.Logger;

/** Sin esto, @Retry reintenta en silencio. La versión Python registra cada intento: aquí también. */
@Dependent
public class LogRetryHandler implements BeforeRetryHandler {

    private static final Logger LOG = Logger.getLogger(OrderEventsConsumer.class);

    @Override
    public void handle(ExecutionContext context) {
        LOG.warnf("Falla transitoria, reintento en 2 s: %s", context.getFailure().getMessage());
    }
}
