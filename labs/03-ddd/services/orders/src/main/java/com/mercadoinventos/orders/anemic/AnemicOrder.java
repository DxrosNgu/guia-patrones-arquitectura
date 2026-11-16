package com.mercadoinventos.orders.anemic;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ❌ VERSIÓN INGENUA: modelo ANÉMICO. Solo datos, getters y setters.
 * Cualquiera puede poner cualquier total, cualquier estado y cualquier cantidad.
 * Las reglas quedan repartidas en los servicios... y cada uno se acuerda de algunas.
 */
@Entity
@Table(name = "anemic_orders")
public class AnemicOrder {

    @Embeddable
    public static class Line {
        private String sku;
        private double unitPrice;   // ❌ double para dinero
        private int quantity;       // ❌ sin límite

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public double getUnitPrice() { return unitPrice; }
        public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }

    @Id
    private UUID id;
    private String customerId;
    private String status;          // ❌ un String: acepta "PAGADO", "paid", "lo que sea"
    private double total;           // ❌ un campo que alguien tiene que acordarse de recalcular
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "anemic_order_lines", joinColumns = @JoinColumn(name = "order_id"))
    private List<Line> lines = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }
    public List<Line> getLines() { return lines; }
    public void setLines(List<Line> lines) { this.lines = lines; }
}
