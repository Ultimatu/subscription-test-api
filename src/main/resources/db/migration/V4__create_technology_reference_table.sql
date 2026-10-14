-- Technologies become reference data: adding one is an insert, not a code change.
create table technology (
    code       varchar(20)  primary key,
    label      varchar(100) not null,
    version    bigint       not null,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    created_by varchar(100) not null,
    updated_by varchar(100) not null,
    constraint ck_technology_code check (code ~ '^[A-Z0-9_]+$')
);

insert into technology (code, label, version, created_at, updated_at, created_by, updated_by)
select code, label, 0, now(), now(), 'system', 'system'
from (values
    ('FTTH',      'Fibre optique'),
    ('SATELLITE', 'Satellite'),
    ('4G_BOX',    'Box 4G')
) as catalogue (code, label);

alter table offer drop constraint ck_offer_technology;
alter table offer add constraint fk_offer_technology foreign key (technology) references technology (code);
