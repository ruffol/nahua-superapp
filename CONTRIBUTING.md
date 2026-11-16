# Contribuir a Nahua

Gracias por querer sumarte. Este proyecto busca ser un ejemplo claro y mantenible de una super app
Android, así que priorizamos claridad sobre ingenio.

## Antes de abrir un PR

1. Abre un issue describiendo el problema o la propuesta (una línea alcanza para empezar).
2. Crea una rama: `feat/mi-cambio`, `fix/mi-arreglo` o `docs/mi-doc`.
3. Corre `./gradlew testDebugUnitTest assembleDebug` localmente.
4. Escribe commits en imperativo y en español o inglés, sin mezclar idiomas en un mismo commit.

## Estilo

- Kotlin idiomático, sin `!!`, con nombres en inglés para código y español para UI.
- Componentes de Compose pequeños y sin lógica de negocio; la lógica vive en ViewModels,
  repositorios o modelos puros.
- Dinero siempre en centavos (`Long`); nunca `Double`.
- Cada nuevo repositorio o regla de negocio necesita tests unitarios.

## Qué buscamos

- Nuevas mini-apps (con su manifiesto en `assets/miniapps/index.json`).
- Mejoras de accesibilidad (contraste, tamaños táctiles, TalkBack).
- Tests de UI con Compose.
- Documentación de arquitectura y de la API de mini-apps.

## Reportar bugs

Incluye dispositivo, versión de Android, pasos para reproducir y, si puedes, un video corto.
