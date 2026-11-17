-- Datos de ejemplo. Los depósitos de OCTUBRE se guardaron con el formato v1 del evento FundsDeposited
-- ({"amountCents": 50000}), antes de que el equipo lo cambiara a v2 ({"amount", "currency", "channel"}).
-- Nadie los reescribió: el upcaster los convierte al leerlos. Ver Upcaster.java.
INSERT INTO events (stream_id, version, type, schema_version, payload, metadata, occurred_at) VALUES
  ('wallet-ana', 1, 'WalletOpened',       1, '{"owner": "ana"}',                                                       '{"seed": true}', '2026-10-01 09:00-05'),
  ('wallet-ana', 2, 'FundsDeposited',     1, '{"amountCents": 50000}',                                                 '{"seed": true}', '2026-10-03 10:15-05'),
  ('wallet-ana', 3, 'FundsWithdrawn',     1, '{"amount": 120.00, "description": "2 × Paraguas solar y envío"}',          '{"seed": true}', '2026-10-15 18:40-05'),
  ('wallet-ana', 4, 'FundsDeposited',     1, '{"amountCents": 20000}',                                                 '{"seed": true}', '2026-10-28 08:05-05'),
  ('wallet-ana', 5, 'FundsDeposited',     2, '{"amount": 80.00, "currency": "PEN", "channel": "YAPE", "description": "Recarga"}', '{"seed": true}', '2026-11-05 12:30-05'),
  ('wallet-ana', 6, 'FundsWithdrawn',     1, '{"amount": 300.00, "description": "3 × Lámpara de agua salada y envío"}',  '{"seed": true}', '2026-11-10 20:10-05'),
  ('wallet-ana', 7, 'WithdrawalRejected', 1, '{"amount": 500.00, "balance": 360.00, "reason": "INSUFFICIENT_FUNDS"}',   '{"seed": true}', '2026-11-12 21:00-05'),
  ('wallet-beto', 1, 'WalletOpened',      1, '{"owner": "beto"}',                                                      '{"seed": true}', '2026-11-02 11:00-05'),
  ('wallet-beto', 2, 'FundsDeposited',    2, '{"amount": 100.00, "currency": "PEN", "channel": "TARJETA", "description": "Primera recarga"}', '{"seed": true}', '2026-11-02 11:05-05');

INSERT INTO naive_wallets VALUES ('naive-beto', 'beto', 100.00);
