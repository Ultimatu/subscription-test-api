-- Prix mensuel figé au moment de la souscription (ADR-0018) : une modification du prix de l'offre
-- ne change ni le prix des souscriptions existantes, ni leur attestation, ni le revenu calculé.
-- Les souscriptions antérieures à cette migration reprennent le prix actuel de leur offre, seule
-- information disponible.
alter table subscription add column monthly_price numeric(12, 2);
update subscription s set monthly_price = o.monthly_price from offer o where o.id = s.offer_id;
alter table subscription alter column monthly_price set not null;
alter table subscription
    add constraint ck_subscription_monthly_price check (monthly_price > 0);
