-- Deux technologies ne peuvent pas porter le même libellé, sans tenir compte de la casse ni des
-- accents : « Telephonie fixe » et « Téléphonie Fixe » sont un doublon (ADR-0006).

-- unaccent() ne peut pas servir dans un index : PostgreSQL la déclare STABLE, parce que son résultat
-- dépend du dictionnaire trouvé par le search_path. Cette fonction nomme le dictionnaire, ce qui
-- permet de la déclarer IMMUTABLE. Elle est créée dans le schéma des migrations (public, ou test pour
-- les tests) ; l'extension, elle, est toujours dans public (V6).
create function immutable_unaccent(value text) returns text
    language sql immutable parallel safe strict
    as $$ select public.unaccent('public.unaccent'::regdictionary, value) $$;

-- Une base existante peut déjà contenir des doublons (données de test, par exemple). Le plus ancien
-- garde son libellé, les autres reçoivent leur code en suffixe : la migration n'échoue pas et ne
-- perd aucune donnée.
update technology duplicate
set label      = left(duplicate.label, 100 - length(duplicate.code) - 3) || ' (' || duplicate.code || ')',
    version    = duplicate.version + 1,
    updated_at = now(),
    updated_by = 'system'
where exists (select 1
              from technology original
              where lower(immutable_unaccent(original.label)) = lower(immutable_unaccent(duplicate.label))
                and (original.created_at, original.code) < (duplicate.created_at, duplicate.code));

-- Même ordre que la recherche (ADR-0017) : sans accents d'abord, puis en minuscules.
create unique index uk_technology_label on technology (lower(immutable_unaccent(label)));
