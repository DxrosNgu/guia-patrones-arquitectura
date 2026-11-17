package com.mercadoinventos.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mercadoinventos.wallet.es.StoredEvent;
import com.mercadoinventos.wallet.es.Upcaster;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UpcasterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private StoredEvent stored(String type, int schemaVersion, String payload) throws Exception {
        return new StoredEvent(1, "wallet-ana", 2, type, schemaVersion, (ObjectNode) mapper.readTree(payload),
            mapper.createObjectNode(), Instant.parse("2026-10-03T15:15:00Z"));
    }

    @Test
    void a_v1_deposit_in_cents_is_read_as_a_v2_deposit() throws Exception {
        StoredEvent v1 = stored("FundsDeposited", 1, "{\"amountCents\": 50000}");

        StoredEvent current = Upcaster.upcast(v1);

        assertThat(current.schemaVersion()).isEqualTo(2);
        assertThat(current.payload().toString())
            .isEqualTo("{\"amount\":500.00,\"currency\":\"PEN\",\"channel\":\"DESCONOCIDO\",\"description\":null}");
        assertThat(v1.payload().toString()).as("el evento guardado no se toca").isEqualTo("{\"amountCents\":50000}");
    }

    @Test
    void events_already_in_the_current_format_pass_through_unchanged() throws Exception {
        StoredEvent v2 = stored("FundsDeposited", 2, "{\"amount\": 80.00, \"currency\": \"PEN\", \"channel\": \"YAPE\"}");

        assertThat(Upcaster.upcast(v2)).isSameAs(v2);
    }
}
