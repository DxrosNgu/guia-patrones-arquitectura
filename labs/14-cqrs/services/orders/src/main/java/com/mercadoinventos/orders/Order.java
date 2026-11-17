package com.mercadoinventos.orders;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * El modelo de ESCRITURA: normalizado, pensado para validar y guardar. No tiene el estado del pago
 * ni del envío: esos datos son de otros servicios. Para consultarlos juntos está el lado de lectura.
 */
@Entity
@Table(name = "orders")
public class Order extends PanacheEntityBase {

    @Embeddable
    public static class Item {
        public String sku;
        public String name;
        public int quantity;
        public BigDecimal unitPrice;
    }

    @Id
    public UUID id;
    public String customerId;
    public String status;
    public BigDecimal total;
    public Instant createdAt;
    /** Versión del agregado: 1 al crearse. El lado de lectura la usa para "leer lo que escribí". */
    public int version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    public List<Item> items = new ArrayList<>();
}
