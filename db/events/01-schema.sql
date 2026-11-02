create table events (
    id serial primary key,
    name varchar(100) not null,
    venue varchar(100) not null,
    starts_at timestamp not null,
    price numeric(10, 2) not null check ( price >= 0 )
);

create table seats (
    id serial primary key,
    event_id int not null references events(id),
    seat_label varchar(10) not null,
    status varchar(10) not null default 'FREE' CHECK ( status in ('FREE', 'RESERVED') ),
    unique (event_id, seat_label)
);

create table reservations (
    id serial primary key,
    seat_id int not null references seats(id),
    user_id int not null, -- ref spre accounts_db.users
    status varchar(10) not null default 'ACTIVE' check ( status in ('ACTIVE', 'CANCELLED')),
    created_at timestamp not null default now()
);