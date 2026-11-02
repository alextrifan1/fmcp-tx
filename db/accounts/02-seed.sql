INSERT INTO users (username, email)
VALUES ('alice', 'alice@test.com'),
       ('bob', 'bob@test.com'),
       ('carol', 'carol@test.com');

INSERT INTO wallets (user_id, balance)
VALUES (1, 500.00),
       (2, 300.00),
       (3, 20.00); -- carol e săracă intenționat: scenariul de rollback