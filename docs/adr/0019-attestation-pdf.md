# ADR-0019 — Attestation de souscription au format PDF

- **Statut** : Acceptée et mise en œuvre (15/10/2026)

## Contexte

Un conseiller doit pouvoir remettre au client un document qui atteste sa souscription : numéro de ligne, offre, prix, dates, et, après une résiliation, sa date et son motif. L'énoncé ne le demande pas : c'est une **extension au-delà de l'énoncé**, réalisée parce que le délai laissé pour le test le permettait. Elle ne modifie pas son contrat d'API : un endpoint est ajouté, aucun n'est changé.

Le terme d'« attestation » a été préféré à celui de « reçu » : un reçu prouve un paiement, et le modèle ne connaît ni facture ni paiement.

## Décision

- **Endpoint** : `GET /api/subscriptions/{id}/attestation` renvoie le PDF (`application/pdf`) en pièce jointe, nommé d'après la référence (`attestation-SUB-2026-000001.pdf`). Une souscription résiliée donne une *attestation de résiliation*, avec la date de fin et le motif.
- **Génération à la demande, sans stockage** : toutes les données sont en base, et le prix est figé sur la souscription ([ADR-0018](0018-prix-fige-a-la-souscription.md)) ; régénérer le document donne donc toujours un résultat exact. Une génération prend environ 70 ms.
- **Architecture** : le port d'entrée `AttestationUseCases` et son service rassemblent les données (souscription, historique, coordonnées du titulaire, libellé de la technologie) ; le port de sortie `AttestationRenderer` les met en page. Le format PDF et la mise en page relèvent de l'infrastructure (`infrastructure/document`).
- **Mise en page** : un gabarit HTML et CSS (Thymeleaf, `templates/documents/attestation.html`), converti en PDF par **openhtmltopdf** (sur Apache PDFBox). Le gabarit s'ouvre aussi tel quel dans un navigateur, avec des valeurs d'exemple, pour travailler la mise en page. Les données sont mises en forme en Java (`AttestationModel` : montants en FCFA, dates en français, numéros groupés par deux), le gabarit ne fait qu'afficher.
- **Charte** : orange et noir, police Helvetica (l'une des polices standard du PDF : aucun fichier de police à embarquer). Ni logo ni marque d'un opérateur réel : le document porte le nom de l'application et la mention « document de démonstration, sans valeur contractuelle ».
- **Préchauffage** : la toute première génération sur une machine prend plusieurs secondes, le temps que PDFBox indexe les polices du système (index ensuite conservé dans `~/.pdfbox.cache`). Elle est faite au démarrage de l'application, en arrière-plan, pour que le premier téléchargement soit immédiat.

## Alternatives envisagées

- **iText** : la référence historique, mais sous licence AGPL, incompatible avec un logiciel commercial non publié.
- **Flying Saucer** : même principe qu'openhtmltopdf, mais sa version actuelle exige Java 21.
- **JasperReports** : adapté aux rapports complexes, mais lourd et doté de son propre format de gabarit.
- **Génération côté navigateur** : chaque client de l'API devrait refaire la mise en page, et le document ne serait pas joignable à un email envoyé par le serveur.
- **Génération en tâche de fond (file de messages, Redis)** : inutile pour un document d'une page produit en quelques dizaines de millisecondes ; le téléchargement deviendrait asynchrone (création d'une tâche, attente, puis récupération) et l'application dépendrait d'un service de plus. Une file se justifierait pour une génération en masse, comme des relevés mensuels.

## Conséquences

- Deux dépendances de plus : Thymeleaf (déjà intégré à Spring Boot) et openhtmltopdf (licence LGPL, compatible avec un usage comme bibliothèque).
- Le gabarit doit rester du XHTML bien formé (balises fermées, pas d'entités nommées) et n'utiliser que du CSS 2.1 : la mise en page repose sur des tableaux, sans flexbox.
- Les polices standard du PDF ne couvrent que le jeu de caractères latin occidental : suffisant pour le français, mais un nom en écriture non latine nécessiterait d'embarquer une police.
