-- Depuis la recherche insensible aux accents (V6), le filtre par ville compare lower(unaccent(city)) :
-- un index sur la colonne brute ne peut pas servir cette expression. Il ne faisait plus que
-- ralentir les écritures. Aux volumes visés, le parcours de la table suffit ; au-delà, il faudrait
-- un index d'expression (voir docs/base-de-donnees.md).
drop index ix_client_city;
