package com.mercadoinventos.orders.ddd.application;

import com.mercadoinventos.orders.ddd.domain.Order;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

/** Un repositorio por AGREGADO (no por tabla): guarda y carga el pedido completo, con sus líneas. */
@ApplicationScoped
public class OrderRepository implements PanacheRepositoryBase<Order, UUID> {
}
