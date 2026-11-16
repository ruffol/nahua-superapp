# Changelog

Todas las versiones notables de Nahua. El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto usa [SemVer](https://semver.org/lang/es/).

## [0.3.0] — 2026-11-15

### Añadido

- **Mapa offline de Colima**: 2,151 teselas empaquetadas (z9–13 estatal, z14–15 metropolitano) con
  respaldo en línea de OpenStreetMap y atribución.
- **GPS**: proveedor nativo basado en `LocationManager`, puente `Nahua.location()` y botón
  "Mi ubicación" en el mapa con permiso en tiempo de ejecución.
- **Tianguis de Colima**: catálogo de productores de Colima, Villa de Álvarez, Comala, Tecomán,
  Manzanillo y Armería; el mapa muestra sus mercados y horarios.
- **Actualizaciones OTA**: consulta de GitHub Releases, descarga con progreso, verificación SHA-256
  e instalación con `PackageInstaller`. Sección *Perfil → Versión*.
- Workflow `release.yml`: al empujar un tag `v*` compila y publica el APK en la release.
- Llave de firma de demo compartida entre CI y builds locales para que las actualizaciones instalen.
- 7 tests nuevos (versiones y teselas offline); 40 en total.

### Cambiado

- La app pasa a estar enfocada en Colima (mapa, tianguis y textos).
- "Nahua IA" se renombra a **Nahuanotoka**.
- Versión 0.3.0 (versionCode 300).

## [0.2.0] — 2026-11-15

### Añadido

- **Mapas** (mini-app): Leaflet + OpenStreetMap con tianguis de CDMX, Oaxaca, Puebla, Mérida y
  Guadalajara, búsqueda por ciudad y popups con especialidad de cada mercado.
- **Axolote** (mini-app): mascota virtual con comida, ánimo, energía y limpieza; decaimiento por
  tiempo, XP, cinco etapas y persistencia en `localStorage`.
- **Tianguis** (mini-app): catálogo de productores de cuatro regiones, canasta y pago con cartera.
- **Nahua IA** (feature nativa): RAG local sobre PDFs con PDFBox-Android, chunking con traslape,
  recuperación léxica BM25 y cliente compatible con OpenAI (OpenCode Go por defecto).
- Nueva categoría **Tianguis** en el hub y cuatro servicios nuevos en el catálogo.
- `docs/RAG.md` con el pipeline, la configuración de la API y los límites.
- Tests de chunking, retrieval y configuración del endpoint (14 nuevos, 33 en total).

### Cambiado

- README reescrito para la nueva oferta de servicios.
- Versión de la app a 0.2.0 (versionCode 2).

## [0.1.0] — 2026-11-15

### Añadido

- Shell de super app en Kotlin + Jetpack Compose (Material 3).
- Hub de servicios con catálogo, categorías y búsqueda.
- Cartera con DataStore, recargas y movimientos.
- Runtime de mini-apps (WebView aislado + puente de pagos).
- Mini-apps Ride, Food, Pagar servicios y Cine.
- Escáner QR, avisos, perfil, tema claro/oscuro.
- CI en GitHub Actions y 19 tests unitarios.