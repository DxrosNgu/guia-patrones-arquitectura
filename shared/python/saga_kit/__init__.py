"""Kit de mensajería para los participantes Python de la saga.

Reúne lo que ya construimos en labs anteriores para no repetirlo en cada servicio:
  - envelope.py : el formato común de los eventos
  - outbox.py   : Transactional Outbox + Polling Publisher (lab 15)
  - inbox.py    : Idempotent Consumer (lab 12)
  - consumer.py : consumidor de Kafka con DLQ y reintentos (lab 06)
"""
