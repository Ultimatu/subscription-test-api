-- Une technologie peut être retirée du catalogue (ADR-0022) : elle n'accepte alors plus de nouvelle
-- offre ni de nouvelle souscription, sans effet sur l'existant. Les technologies existantes restent
-- actives.
alter table technology add column active boolean not null default true;
