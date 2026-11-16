package com.mercadoinventos.inventory;

import java.util.List;

/** De dónde sacar los ítems cuando el evento solo avisa (Event Notification). */
public interface OrderItemsSource {

    List<Item> itemsOf(String orderId);
}
