# BeatMyBeat KMP Migration Plan

## Objetivo

Migrar la app movil a la estructura Kotlin Multiplatform en `frontend`, manteniendo Android estable mientras se habilita una base compartida para iOS.

## Estado actual

- Fase 1 completada: la app Android funcional ya vive en `frontend/composeApp/src/androidMain`.
- Modulo Android legacy eliminado (segun ultimo estado de trabajo).
- Build Android valida desde `frontend`: `:composeApp:assembleDebug`.

## Principios de migracion

- Priorizar continuidad de Android en cada iteracion.
- Extraer primero logica pura a `commonMain`.
- Encapsular APIs de plataforma mediante `expect/actual` o interfaces.
- Hacer cambios incrementales y verificables por fase.

## Fase 1 - Consolidacion Android en KMP (completada)

### Objetivo

Centralizar la app Android en `frontend/composeApp` sin introducir cambios funcionales.

### Alcance

- Mover `MainActivity`, UI, servicios, networking, notificaciones y recursos a `androidMain`.
- Ajustar Gradle/dependencias en `frontend`.
- Validar build de Android dentro de `frontend`.

### Criterio de cierre

- APK debug compila y abre desde `frontend`.
- No dependencia operativa del modulo Android antiguo.

## Fase 2 - Extraccion de dominio y modelos a commonMain

### Objetivo

Reducir acoplamiento Android moviendo logica compartible a `commonMain`.

### Alcance

- Crear modulo/capa de dominio compartida (modelos, reglas, casos de uso).
- Migrar utilidades puras (parsing, validaciones, normalizacion de strings).
- Mantener UI y servicios de reproduccion/descarga aun en `androidMain`.

### Entregables

- Paquete `commonMain` con modelos y casos de uso compartidos.
- Android consumiendo esos casos de uso en lugar de logica embebida en pantallas.

### Criterio de cierre

- Build Android OK.
- Cobertura minima de tests de dominio en `commonTest`.

## Fase 3 - Abstracciones multiplataforma (expect/actual)

### Objetivo

Definir contratos para capacidades de plataforma.

### Alcance

- Interfaces/`expect` para:
  - almacenamiento local
  - reproduccion de audio
  - notificaciones
  - permisos
  - acceso a libreria multimedia
- Implementacion `actual` Android sin cambiar comportamiento actual.

### Criterio de cierre

- Android funcionando con la nueva capa de abstraccion.
- Dependencias directas a `android.*` fuera de `androidMain` eliminadas.

## Fase 4 - Base iOS funcional

### Objetivo

Crear implementaciones `iosMain` para los contratos definidos en Fase 3.

### Alcance

- Implementar almacenamiento, networking y reproducción iOS.
- Integrar UI compartida en `iosApp` (con adaptaciones necesarias).
- Resolver permisos/ciclo de vida iOS.

### Criterio de cierre

- App iOS arrancando y navegando pantallas principales.
- Flujos core (analizar/buscar/reproducir) funcionando al menos en modo baseline.

## Fase 5 - Paridad funcional y endurecimiento

### Objetivo

Acercar funcionalidades Android/iOS y preparar release estable.

### Alcance

- Paridad de features prioritarias.
- Ajustes de UX por plataforma.
- Hardening: errores, offline, telemetria basica, testing.

### Criterio de cierre

- Checklist de release cumplida en ambas plataformas.
- Regresion controlada y documentacion actualizada.

## Riesgos y mitigaciones

- **Acoplamiento Android alto**: migrar por capas, no por carpetas completas.
- **Regresiones en player/download**: mantener servicios Android intactos hasta Fase 4.
- **Sobrecarga tecnica**: limitar cada PR a una extraccion concreta y verificable.

## Checklist de control por fase

- Build Android (`:composeApp:assembleDebug`)
- Smoke test UI principal
- Verificacion de permisos/notificaciones/reproduccion
- Actualizacion de documentacion tecnica

## Proximo paso sugerido

Iniciar Fase 2 con una primera extraccion de bajo riesgo:

1. Modelos de red y dominio (DTOs y entidades).
2. Utilidades puras (`cleanArtistForLyrics`, parseos y validaciones).
3. Tests `commonTest` para estas reglas.

## Resume Work Here

### Estado exacto actual

- Fase 1 completada: app Android integrada en `frontend/composeApp/src/androidMain`.
- Modulo Android legacy eliminado del flujo de trabajo.
- Build Android validada en `frontend` con:
  - Windows: `.\gradlew.bat :composeApp:assembleDebug`
  - macOS/Linux: `./gradlew :composeApp:assembleDebug`
- Resultado esperado actual: `BUILD SUCCESSFUL` en `:composeApp:assembleDebug`.

### Siguiente paso accionable (primer ticket)

Extraer utilidades puras de negocio a `commonMain` sin tocar comportamiento:

1. Crear paquete compartido para utilidades de parsing/normalizacion.
2. Mover `cleanArtistForLyrics` y funciones de parseo relacionadas a ese paquete.
3. Actualizar imports en Android para usar la nueva ubicacion.
4. Añadir tests en `commonTest` para casos base y edge cases.
5. Revalidar build Android (`:composeApp:assembleDebug`).

### Bloqueos y decisiones abiertas

- Definir si en Fase 2 se mantiene `OkHttp + org.json` temporalmente o se migra parte de red a KMP puro (`Ktor + kotlinx.serialization`) desde el inicio.
- Confirmar prioridad funcional post-migracion: estabilizacion de bugs de player/download vs avance de arquitectura KMP.
- Decidir convencion de paquetes compartidos (`domain`, `core`, `shared`) para evitar refactor adicional posterior.
- Alinear alcance iOS baseline (solo lectura/reproduccion local o tambien descargas) antes de iniciar Fase 4.

