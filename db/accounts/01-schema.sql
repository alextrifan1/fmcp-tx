create table users (
    id serial primary key,
    username varchar(50) not null unique,
    email varchar(100) not null
);

create table wallets (
    id serial primary key,
    user_id int not null unique references users(id),
    balance numeric(10, 2) not null default 0 check ( balance >= 10 )
);

create table payments (
    id serial primary key,
    wallet_id int not null references wallets(id),
    amount numeric(10, 2) not null,
    type varchar not null check ( type in ('PURCHASE', 'REFUND', 'TOPUP') ),
    reservation_id int, -- ref spre events_db.reservations
    created_at timestamp not null default now()
);