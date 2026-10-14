alter table client
    add column deleted_at timestamptz,
    add column deleted_by varchar(100);

alter table subscription
    add column deleted_at timestamptz,
    add column deleted_by varchar(100);

-- The email stays unique among existing clients only, so it can be reused once a client is deleted.
drop index uk_client_email;
create unique index uk_client_email on client (lower(email)) where deleted_at is null;
