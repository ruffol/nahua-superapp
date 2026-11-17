# Evaluación con el corpus real

Cinco obras, 535 páginas, extraídas del PDF y guardadas en `app/src/test/resources/eval/corpus.json`
(1.4 MB). El banco corre siempre en JVM: `./gradlew :app:testDebugUnitTest --tests '*CorpusBenchmarkTest*'`.

## Recuperación (BM25 + limpieza + ventanas)

| Caso | Pregunta | Páginas esperadas | Resultado |
| --- | --- | --- | --- |
| C1 | territorio cuerpo-tierra | 7 | ✅ p7 |
| C2 | pedagogía de la crueldad | 23, 81 | ✅ p81 (y 23 en el top 6) |
| C3 | campaña por el salario doméstico | 33, 36 | ❌ p37, 51, 23… |
| C4 | patriarcado, colonialidad, comunidad | 4 obras | ✅ 4 obras, modo balanceado |
| C5 | colonialidad y patriarcado moderno | 113 | ✅ p113 (en el top 10) |
| C6 | fórmula de la fotosíntesis | — | ✅ evidencia débil + cobertura |
| C7 | huelga en la lucha | 62, 63 | ✅ p63 y 62 en el top |

**4/5** casos con página esperada. C3 es un fallo conocido: la página 33 compite con el capítulo de
salarios y BM25 prefiere páginas donde “salario” aparece muchas veces. Se arreglaría con embeddings
(hoy no hay proveedor que los ofrezca en OpenCode Go: `/embeddings` responde 404) o con un índice
de frases. Queda registrado, no escondido.

## Generación (API real, OpenCode Go)

| Caso | Modelo | Citas | Notas |
| --- | --- | --- | --- |
| C1 | deepseek-v4.1-flash | 7 | Definición de territorio cuerpo-tierra con p7 |
| C2 | deepseek-v4.1-flash | 5 | “pedagogía de la crueldad” explicada con pp. 81/73 |
| C7 | deepseek-v4.1-flash | 17 | Huelga y salario, con las citas del capítulo |
| C4 | deepseek-v4.1-flash | 28 | Tabla por eje y obra, celdas “no se pronuncia” |

## El detalle que casi rompe la app

`deepseek-v4.1-flash` razona antes de responder (`reasoning_content`). En una consulta de
comparación, el razonamiento consumió 5 700–8 200 tokens y el `content` llegó vacío con
`finish_reason=length`. La app mostraba una tarjeta en blanco. Ahora:

1. Si `content` está vacío y hay razonamiento con `finish_reason=length`, se reintenta una vez con el
   doble de `max_tokens` (tope 16 000).
2. Si el reintento tampoco responde, el error dice qué pasó y sugiere un modelo sin razonamiento
   (`claude-haiku-5-5` responde sin razonamiento; `glm-5.3-flash` razona y no responde ni con 1 200).
3. Presupuestos: correlación 8 000, guion/preguntas 4 500, resumen/ideas/glosario 4 000, fichas 2 500.

Medido con la cuenta del usuario: C4 tardó ~55 s y costó ≈ 0.0026 USD por comparación.

## Reproducir

1. Configura la key en la app (Ajustes) o exporta `NAHUA_API_KEY`.
2. `./gradlew :app:testDebugUnitTest --tests '*CorpusBenchmarkTest*'` y revisa
   `app/build/eval/diag.txt`.
3. Para las respuestas reales, usa `docs/eval/live_eval.py` (no incluido con la key; lee la variable
   de entorno `NAHUA_API_KEY`).
