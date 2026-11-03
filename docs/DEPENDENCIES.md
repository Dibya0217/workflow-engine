# Dependency compatibility

The backend deliberately stays on Spring Boot **3.x** as requested. Boot 3.5.7 manages the compatible Spring Framework, Spring Security, Spring Data, Spring Kafka, Jackson, Hibernate, Flyway, Micrometer and Testcontainers versions through its BOM. Java release target and actual final test runtime are 21. The Springdoc 2.8 line supports Boot 3.5; 2.8.14 is pinned. Maven Wrapper pins 3.9.11. Do not independently update the Spring-managed libraries without re-running integration tests.

The frontend pins React 19.1.1, TypeScript 5.9.2, Vite 7.1.7, Vite React plugin 5.0.3, Tailwind 4.1.13, React Flow 12.8.5, Axios 1.12.2 and React Router 7.9.1. Node 22.12+ is required (Docker uses Node 22). The pnpm lockfile records the resolved dependency graph. React Flow supports React 19; the production TypeScript/Vite build verifies their shared types and bundling. Tailwind 4 uses its Vite plugin rather than obsolete Tailwind 3 configuration.

The final checks use real PostgreSQL and Kafka. PostgreSQL 16 is the deployment and Testcontainers target; the native test fallback is the PostgreSQL binary provided by Zonky. Apache Kafka 3.9.1 is the Compose broker, compatible with the Boot-managed Kafka client. Single-broker replication is 1 for local development, not a resilient production cluster. Redis is deliberately outside the correctness path.

Primary compatibility references checked during implementation:
- https://docs.spring.io/spring-boot/3.5/system-requirements.html
- https://springdoc.org/v2/ (compatibility matrix)
- https://vite.dev/guide/ (Node requirements)
- https://reactflow.dev/learn (React Flow documentation)
- https://kafka.apache.org/39/getting-started/docker/ (official Kafka 3.9.1 image)

Versions are reproducible pins, not a claim of perpetual security support. Upgrade within supported lines with the integration suite and a dependency advisory scan before public deployment.
