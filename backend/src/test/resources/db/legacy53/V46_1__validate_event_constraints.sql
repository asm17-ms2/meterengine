-- V45, V46과 다른 트랜잭션에서 검증해 event 전체 검사 중에도 수집이 막히지 않게 한다.
ALTER TABLE event VALIDATE CONSTRAINT event_environment_fk;
ALTER TABLE event VALIDATE CONSTRAINT event_customer_same_environment_fk;
ALTER TABLE event VALIDATE CONSTRAINT event_actor_type_check;
ALTER TABLE event VALIDATE CONSTRAINT event_actor_identity_check;
