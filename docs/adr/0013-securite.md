# ADR-0013 — Authentification JWT avec les rôles ADMIN et CONSEILLER

- **Statut** : Acceptée et mise en œuvre (14/10/2026)

## Contexte

L'énoncé ne demande pas d'authentification, mais l'audit ([ADR-0004](0004-audit-et-historique.md)) n'a de sens que si l'on sait qui agit. L'API est un outil de back-office : ce sont des conseillers et des administrateurs qui créent les clients, activent ou résilient les lignes et consultent les statistiques. Les clients finaux ne l'utilisent pas directement.

## Décision

- Authentification par **jeton JWT**, avec le support natif de Spring Security (`spring-boot-starter-oauth2-resource-server`), sans bibliothèque JWT tierce.
- Deux rôles : **ADMIN** (catalogue des offres et des technologies, suppressions, statistiques) et **CONSEILLER** (clients et souscriptions).
- La sécurité est **désactivable par une propriété**, et la collection Postman livrée se connecte et transmet le jeton automatiquement : le contrat d'API de l'énoncé reste testable tel quel.
- Swagger UI et le point de santé restent publics.

## Mise en œuvre

- **Comptes** : table `app_user` (migration V8), avec un mot de passe stocké sous forme d'empreinte BCrypt, préfixée par son algorithme (`{bcrypt}…`) pour pouvoir en changer sans invalider les comptes. Deux comptes de démonstration : `admin` / `Admin@2026` et `conseiller` / `Conseiller@2026`. Les comptes relèvent de l'infrastructure (`infrastructure/security`), pas du domaine.
- **Connexion** : `POST /api/auth/login` renvoie `{ accessToken, tokenType, expiresIn, roles }`. Un compte inconnu et un mauvais mot de passe donnent la même réponse (401 `INVALID_CREDENTIALS`), pour ne pas révéler quels comptes existent. Le mot de passe n'apparaît jamais dans les logs (`LoginRequest.toString()` le masque).
- **Jeton** : JWT signé en HS256, valable une heure, avec le nom de l'utilisateur dans `sub`, ses rôles dans `roles` et l'émetteur `subscription-api` dans `iss`. La signature, la date d'expiration et l'émetteur sont vérifiés à chaque requête. La clé (256 bits au moins) vient de `app.security.jwt.secret` ; en production, de la variable d'environnement `APP_SECURITY_JWT_SECRET`.
- **Droits** :

  | Action | CONSEILLER | ADMIN |
  |---|---|---|
  | Lire clients, offres, technologies et souscriptions | ✅ | ✅ |
  | Créer et modifier un client | ✅ | ✅ |
  | Souscrire et changer le statut d'une souscription | ✅ | ✅ |
  | Supprimer un client ou une souscription | ❌ | ✅ |
  | Créer, modifier ou désactiver une offre ; ajouter une technologie | ❌ | ✅ |
  | Consulter les statistiques | ❌ | ✅ |
  | Se connecter, Swagger UI, `/actuator/health` | public | public |

- **Erreurs** : 401 (`AUTHENTICATION_REQUIRED` sans jeton, `INVALID_TOKEN` pour un jeton invalide ou expiré) et 403 (`ACCESS_DENIED`), au format Problem Details comme toutes les autres erreurs.
- **Audit** : l'auteur enregistré (`created_by`, `updated_by`, `changed_by`) est le nom de l'utilisateur du jeton ; `anonymous` quand la sécurité est désactivée.
- **Désactivation** : `app.security.enabled=false` (ou `APP_SECURITY_ENABLED=false`) ouvre toute l'API sans jeton.
- **Documentation** : Swagger UI propose un bouton « Authorize » pour saisir le jeton ; les réponses 401 et 403 sont documentées sur chaque endpoint protégé.

## Limites

- Pas de jeton de rafraîchissement : il faut se reconnecter au bout d'une heure.
- Pas de révocation d'un jeton avant son expiration.
- Pas de gestion des comptes par l'API (création, désactivation, changement de mot de passe).
- En production, l'émission des jetons serait confiée à un fournisseur d'identité (Keycloak, Azure AD…) : l'API ne ferait plus que vérifier les jetons, et la table `app_user` disparaîtrait.

## Alternatives envisagées

- **Un rôle CLIENT**, qui permettrait aux clients de consulter leurs propres souscriptions : cela supposerait des comptes clients, une gestion des identifiants et un filtrage par propriétaire. C'est une autre application que celle décrite par l'énoncé.
- **Aucune authentification** : conforme à l'énoncé, mais l'audit perdrait son intérêt.

## Conséquences

- Chaque modification est attribuée à un utilisateur identifié.
- La gestion des utilisateurs reste minimale (utilisateurs de démonstration) ; en production, l'émission des jetons serait déléguée à un fournisseur d'identité.
