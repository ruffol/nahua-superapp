# BASELINE.md · Nahua AI — Línea base de compilación y pruebas

**Commit:** `dac1e01` · **Versión:** 0.8.0 (800) · **Ejecutado:** 17 de noviembre de 2026, 01:42 (America/Mexico_City)

## 1. Entorno

| Componente | Valor observado |
| --- | --- |
| Sistema | Windows 11 (x64) |
| JDK de compilación | jbr-21.0.11 (`~/.jdks/jbr-21.0.11`) |
| Gradle | 9.6.0 (wrapper, distribución en caché) |
| Android Gradle Plugin | 9.5.1 |
| Kotlin | 2.2.10 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| Bytecode | Java 17 |
| Caché de Gradle | `GRADLE_USER_HOME=~/.gradle` (modo `--offline`) |
| Android SDK | `C:\Users\42\AppData\Local\Android\Sdk` (build-tools 35.0.0) |

**Nota de entorno:** el proyecto declara un toolchain JDK 21 y el plugin `foojay-resolver-convention` en
`settings.gradle.kts`. En este equipo el build sólo funciona en **modo offline con el caché del usuario**; con un
`GRADLE_USER_HOME` vacío, Gradle intenta descargar ese plugin y falla sin red. En GitHub Actions (con red) el
mismo build funciona sin cambios.

## 2. Comandos ejecutados

```powershell
$env:JAVA_HOME="C:\Users\42\.jdks\jbr-21.0.11"
$env:GRADLE_USER_HOME="C:\Users\42\.gradle"
./gradlew :app:testDebugUnitTest :app:assembleDebug --offline --console=plain
```

**Resultado:** `BUILD SUCCESSFUL in 2m 50s` (daemon frío). Sin errores de compilación.

Advertencias preexistentes (no bloquean):

- `android.defaults.buildfeatures.resvalues` marcado como deprecado.
- `Project.android(Action<BaseAppModuleExtension>)` deprecado en AGP 9.x.

## 3. Pruebas unitarias

**95 tests · 0 fallos** (JUnit 4, JVM, `testDebugUnitTest`).

| Suite | Tests | Fallos |
| --- | --- | --- |
| BarterValuatorTest | 17 | 0 |
| ServiceCatalogTest | 9 | 0 |
| ContextBuilderTest | 8 | 0 |
| CacheKeyTest | 7 | 0 |
| RetrieverTest | 7 | 0 |
| TextChunkerTest | 6 | 0 |
| WalletLedgerTest | 6 | 0 |
| MoneyTest | 5 | 0 |
| TextCleanerTest | 5 | 0 |
| OfflineMapAssetsTest | 5 | 0 |
| StudyScenarioTest | 4 | 0 |
| RagSettingsTest | 4 | 0 |
| VersionTest | 4 | 0 |
| EpubParserTest | 3 | 0 |
| InboxRepositoryTest | 3 | 0 |
| FormattersTest | 2 | 0 |

Del motor RAG: **44 tests** (CacheKey 7, ContextBuilder 8, Retriever 7, TextChunker 6, TextCleaner 5,
StudyScenario 4, EpubParser 3, RagSettings 4).

## 4. Artefactos

| Artefacto | Ruta | Tamaño / hora |
| --- | --- | --- |
| APK debug | `app/build/outputs/apk/debug/app-debug.apk` | 34.9 MB · 01:42 |
| Reportes de prueba | `app/build/reports/tests/testDebugUnitTest` | HTML + XML por suite |
| Resultados XML | `app/build/test-results/testDebugUnitTest/*.xml` | 16 archivos |

## 5. Qué no se ejecutó (y por qué)

| Prueba | Motivo |
| --- | --- |
| Instrumentadas (`androidTest`) | Requieren emulador/dispositivo dedicado al baseline; no forman parte del CI actual. |
| Importación real de un PDF/EPUB de 1 200 páginas | Requiere corpus del dueño (NA-003). |
| Llamadas reales al proveedor | Consumen cuota y no son deterministas; el baseline se limita a unitarias. |
| `assembleRelease` | R8 tarda varios minutos; el CI de release lo cubre al etiquetar. |

## 6. Reproducir

1. Tener Android SDK 35 y build-tools 35.0.0.
2. JDK 21 (o el toolchain que Gradle resuelva) y el caché de dependencias.
3. `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline`.
4. Comparar con la tabla de la sección 3: cualquier suite que baje de tests o falle es una regresión.

## 7. Referencia de comportamiento (para comparar cambios)

| Escenario | Resultado esperado hoy |
| --- | --- |
| PDF de 8 páginas con texto + “Dame un resumen” | Contexto en modo cobertura, 8 bloques, sin `weak`; respuesta con citas por página |
| Pregunta puntual con término presente | Modo `busqueda`, primera fuente = página correcta |
| Pregunta sin coincidencias | Modo `cobertura`, `weak = true`; el prompt pide declarar el límite |
| Mesa con 4 libros + tarea de correlación | 3 fragmentos por libro (máx. 12), intercalados |
| Repetir la misma consulta sin turnos nuevos | Acierto de caché, 0 tokens, badge CACHÉ |
| Sin API key | Abre configuración; no genera respuesta |
| Error 401/403 | Mensaje del proveedor + configuración abierta |
