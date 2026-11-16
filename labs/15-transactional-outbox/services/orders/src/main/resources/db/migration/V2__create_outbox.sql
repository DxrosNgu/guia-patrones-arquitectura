-- Transactional Outbox: los eventos se guardan en la MISMA base y en la MISMA
-- transacción que el cambio de negocio. Publicarlos es un paso posterior.
--
-- Las columnas id, aggregatetype, aggregateid, type y payload son las que espera
-- el Outbox Event Router de Debezium (modo cdc).
CREATE TABLE outbox (
    id            UUID         PRIMARY KEY,   -- = eventId del envelope
    aggregatetype VARCHAR(50)  NOT NULL,      -- "orders" → topic orders.events
    aggregateid   VARCHAR(64)  NOT NULL,      -- key del mensaje en Kafka
    type          VARCHAR(50)  NOT NULL,      -- OrderPlaced
    payload       TEXT         NOT NULL,      -- el envelope completo, en JSON
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ                 -- solo lo usa el modo polling
);

-- El publicador busca "lo pendiente, en orden": este índice parcial lo hace barato.
CREATE INDEX idx_outbox_pending ON outbox (created_at) WHERE published_at IS NULL;
