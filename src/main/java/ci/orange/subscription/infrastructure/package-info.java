/**
 * Les adaptateurs, qui relient l'application au monde extérieur.
 *
 * <ul>
 *   <li>Entrants, ils appellent les ports d'entrée : {@code web} (HTTP) et {@code scheduling} (tâches
 *       planifiées).
 *   <li>Sortants, ils implémentent les ports de sortie : {@code persistence} (PostgreSQL),
 *       {@code document} (attestation PDF) et {@code notification} (emails).
 *   <li>À part : {@code security} (jetons, droits et comptes de connexion, qui ne relèvent pas du
 *       métier et n'ont donc pas de port) et {@code config} (assemblage des beans).
 * </ul>
 *
 * <p>Dépend de l'application et du domaine, jamais l'inverse. Un adaptateur entrant passe toujours par
 * un port, jamais directement par un adaptateur sortant. Un controller traduit HTTP en appel de port
 * sans prendre de décision métier ; une entité JPA reste distincte du modèle du domaine et s'y convertit
 * par un mapping manuel (ADR-0001).
 */
package ci.orange.subscription.infrastructure;
