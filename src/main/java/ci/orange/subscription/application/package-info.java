/**
 * Les cas d'usage. Les ports d'entrée ({@code port.in}) décrivent ce que l'application sait faire,
 * les ports de sortie ({@code port.out}) ce dont elle a besoin : stockage, numérotation, documents,
 * emails. Les services ({@code service}) implémentent les ports d'entrée.
 *
 * <p>Ne dépend que du domaine. Côté framework, elle se limite à {@code @Service},
 * {@code @Transactional} et à la pagination de Spring Data (ADR-0001) : rien qui touche à HTTP ou à
 * JPA. Un service charge les objets par les ports de sortie, applique les règles dans l'ordre imposé
 * par l'énoncé, enregistre le résultat et porte la transaction ; les règles elles-mêmes restent dans
 * le domaine.
 */
package ci.orange.subscription.application;
