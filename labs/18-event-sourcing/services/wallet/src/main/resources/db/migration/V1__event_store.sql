-- ─────────────────────────────────────────────────────────────────────────
-- EL EVENT STORE: la única fuente de verdad del monedero.
-- No hay columna "balance" en ningún lado: el saldo se CALCULA leyendo estos eventos.
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE events (
    global_position BIGSERIAL   PRIMARY KEY,                         -- orden de llegada (lo usan las proyecciones)
    stream_id       TEXT        NOT NULL,                            -- el agregado: 'wallet-ana'
    version         INT         NOT NULL,                            -- 1, 2, 3… dentro del stream
    type            TEXT        NOT NULL,                            -- 'FundsDeposited'
    schema_version  INT         NOT NULL,                            -- formato del payload (upcasting)
    payload         JSONB       NOT NULL,
    metadata        JSONB       NOT NULL DEFAULT '{}',               -- quién, qué comando, qué intento
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    transaction_id  XID8        NOT NULL DEFAULT pg_current_xact_id(), -- ver "El proyector" en el README
    -- Concurrencia optimista: dos escrituras que leyeron la misma versión no pueden guardar las dos la siguiente.
    CONSTRAINT events_stream_version UNIQUE (stream_id, version)
);
CREATE INDEX events_by_transaction ON events (transaction_id, global_position);

-- Append-only de verdad: ni la aplicación ni alguien con psql puede cambiar o borrar la historia.
CREATE FUNCTION events_are_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'El event store es append-only: % no está permitido', TG_OP;
END $$;
CREATE TRIGGER events_no_update_delete BEFORE UPDATE OR DELETE ON events
    FOR EACH ROW EXECUTE FUNCTION events_are_append_only();
CREATE TRIGGER events_no_truncate BEFORE TRUNCATE ON events
    FOR EACH STATEMENT EXECUTE FUNCTION events_are_append_only();

-- SNAPSHOTS: una foto del estado cada N eventos, para no releer toda la historia en cada comando.
-- Es una caché: se puede borrar entera y todo sigue funcionando (más lento).
CREATE TABLE snapshots (
    stream_id       TEXT        PRIMARY KEY,
    version         INT         NOT NULL,   -- hasta qué evento incluye
    snapshot_schema INT         NOT NULL,   -- si cambia el formato de la foto, las viejas se ignoran
    state           JSONB       NOT NULL,
    taken_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
