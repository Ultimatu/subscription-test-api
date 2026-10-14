-- ND: "27" followed by the next value of this sequence on 8 digits (ADR-0014)
create sequence subscription_nd_seq minvalue 1 maxvalue 99999999 no cycle;

-- Reference: SUB-{year}-{counter on 6 digits}, the counter restarting every year (ADR-0014)
create table subscription_reference_counter (
    year       integer primary key,
    last_value bigint  not null
);

-- Technology copied from the offer, which never changes (rule 10), so that the database
-- itself guarantees a single ongoing subscription per technology for a client (rule 14)
alter table subscription add column technology varchar(20);
update subscription s set technology = o.technology from offer o where o.id = s.offer_id;
alter table subscription alter column technology set not null;
alter table subscription
    add constraint fk_subscription_technology foreign key (technology) references technology (code);

create unique index uk_subscription_client_technology_in_progress
    on subscription (client_id, technology)
    where status in ('PENDING', 'ACTIVE') and deleted_at is null;

create index ix_subscription_start_date on subscription (start_date);
