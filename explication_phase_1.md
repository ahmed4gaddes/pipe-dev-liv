# Comprendre la Phase 1 : Architecture Microservices avec Maven Multi-Modules

La Phase 1 a consisté à transformer notre application Spring Boot classique (un monolithe) en un véritable écosystème de **microservices**. Pour cela, nous avons restructuré le projet en utilisant le format "Maven Multi-Modules".

Voici en détail ce que nous avons mis en place et pourquoi :

---

## 1. Le POM Parent (`backend/pom.xml`)
Au lieu d'être un projet exécutable, le dossier `backend` est devenu le "Parent" de tous les autres microservices.
**Son rôle :**
*   **Centraliser les versions** : Il définit qu'on utilise Java 17, Spring Boot 3.3.2, et Spring Cloud 2023.0.3. Ainsi, aucun microservice n'aura une version différente.
*   **Déclarer les sous-modules** : Il liste les sous-dossiers (`common-lib`, `discovery-server`, `api-gateway`) pour que Maven puisse compiler tout le projet d'un seul coup.

---

## 2. Le Serveur Eureka (`discovery-server`)
**Le Problème :** Dans une architecture avec plusieurs microservices (Ticket Service, User Service, etc.), chacun tourne sur un port différent. Si un service doit en appeler un autre, il ne peut pas coder l'adresse "en dur" (ex: `localhost:8081`), car dans le Cloud, les adresses changent tout le temps.
**La Solution : Eureka (L'annuaire).**
*   C'est le registre central. Quand un microservice démarre, il va s'enregistrer auprès d'Eureka.
*   Si le Ticket Service a besoin de parler au User Service, il demande à Eureka : *"Où est le User Service ?"*, et Eureka lui renvoie l'adresse exacte.
*   **Port configuré :** 8761

---

## 3. L'API Gateway (`api-gateway`)
**Le Problème :** Le frontend (React) ne doit pas avoir à gérer les adresses de 5 microservices différents. De plus, il serait dangereux d'exposer tous les microservices directement sur Internet sans contrôle centralisé.
**La Solution : Spring Cloud Gateway (Le point d'entrée unique).**
*   Le frontend envoie **toutes** ses requêtes à la Gateway sur le port `8080`.
*   **Routage** : La Gateway lit l'URL (ex: `/api/tickets/...`), demande à Eureka où se trouve le Ticket Service, et lui transfère la requête.
*   **Sécurité** : La Gateway est connectée à **Keycloak**. Avant de transférer la requête, elle vérifie que l'utilisateur possède un jeton (JWT) valide. Si ce n'est pas le cas, la requête est rejetée (Erreur 401 Unauthorized) avant même d'atteindre le Ticket Service.
*   **Port configuré :** 8080

---

## 4. La Bibliothèque Partagée (`common-lib`)
**Le Problème :** Plusieurs microservices vont utiliser exactement les mêmes classes. Par exemple, si on crée une classe d'erreur `CustomException`, ou un objet `TicketDto`, on ne veut pas avoir à copier-coller ce code dans chaque microservice.
**La Solution : Common-Lib.**
*   C'est un module Maven classique (qui ne s'exécute pas tout seul).
*   Il contient le code partagé (les DTOs, les configurations de sécurité communes, la gestion des erreurs).
*   Les autres microservices ajouteront `common-lib` dans leurs dépendances pour réutiliser ce code.

---

## 5. Résumé de l'état du Git

Comme pour la phase précédente, nous avons respecté la stratégie **Gitflow** :
1. Nous avons créé une branche temporaire `feature/setup-multi-modules`.
2. J'ai généré tout le code de la Phase 1 sur cette branche.
3. Nous avons poussé cette branche sur GitHub.
4. Vous avez créé une Pull Request vers `develop` et vous l'avez validée.
5. Enfin, nous avons basculé sur `develop` et fait un `git pull` pour rapatrier le code validé sur votre machine locale.

Votre projet est désormais prêt à accueillir les microservices métiers (Ticket Service et User Service) !
