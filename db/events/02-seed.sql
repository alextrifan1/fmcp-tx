INSERT INTO events (name, venue, starts_at, price)
VALUES ('Untold Warmup', 'BT Arena', '2026-12-10 20:00', 120.00),
       ('Concert Filarmonica', 'Casa de Cultura', '2026-12-15 19:00', 60.00);

-- 10 locuri per eveniment: A1..A10
INSERT INTO seats (event_id, seat_label)
SELECT e.id, 'A' || n
FROM events e
         CROSS JOIN generate_series(1, 10) AS n;