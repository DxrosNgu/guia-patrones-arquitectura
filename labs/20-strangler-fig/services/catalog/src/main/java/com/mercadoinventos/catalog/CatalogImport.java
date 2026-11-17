package com.mercadoinventos.catalog;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * La MIGRACIÓN DE DATOS del catálogo: al arrancar con la base vacía, copia los productos del legado.
 * Funciona porque en este lab el catálogo es de solo lectura: si el legado siguiera cambiando precios,
 * habría que sincronizar en continuo, como hace el servicio de pedidos.
 */
@ApplicationScoped
public class CatalogImport {

    private static final Logger LOG = Logger.getLogger(CatalogImport.class);

    @RestClient
    LegacyCatalog legacy;

    void onStart(@Observes StartupEvent event) {
        try {
            int imported = importIfEmpty();
            if (imported > 0) {
                LOG.infof("Copiados %d productos del legado", imported);
            }
        } catch (RuntimeException e) {
            LOG.warnf("No se pudo copiar el catálogo del legado (%s). Reintenta con POST /admin/import", e.getMessage());
        }
    }

    public int importIfEmpty() {
        // Ojo: Product::count (referencia a método) llamaría al count() de la clase base, sin la magia de Panache.
        if (QuarkusTransaction.requiringNew().call(() -> Product.count()) > 0) {
            return 0;
        }
        List<ProductJson> products = legacy.all();
        QuarkusTransaction.requiringNew().run(() -> products.forEach(p -> {
            Product product = new Product();
            product.sku = p.sku();
            product.name = p.name();
            product.price = p.price();
            product.persist();
        }));
        return products.size();
    }
}
