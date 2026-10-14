-- Recherche insensible aux accents (« kone » trouve « Koné », « bouake » trouve « Bouaké »).
-- Extension « trusted » depuis PostgreSQL 13 : le propriétaire de la base peut l'installer sans
-- être superutilisateur. Elle est créée explicitement dans public, schéma partagé par l'application
-- et par les tests (qui tournent dans le schéma test), quel que soit l'ordre de leurs migrations.
create extension if not exists unaccent schema public;
