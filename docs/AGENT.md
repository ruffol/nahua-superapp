# Orquestador de herramientas

Contratos en `core/agent/Tooling.kt`. El objetivo es que la UI nunca arme prompts: pide una
intención, el registro resuelve la herramienta y su presupuesto.

## Piezas

| Pieza | Qué hace |
| --- | --- |
| `AssistantTool` | Envuelve una `StudioTask` con su `ToolBudget` y si usa dos pasadas |
| `ToolRegistry` | Único lugar donde se registran las herramientas (`ToolRegistry.default()`) |
| `ToolBudget` | `maxCalls`, `maxTokens`, `maxSteps` por ejecución |
| `ModelGateway` | Interfaz al proveedor; hoy `ChatClientGateway` sobre `ChatClient` |
| `IntentRouter` | Enrutador determinista de frases a herramientas (sin modelo) |

## Presupuestos actuales

| Herramienta | Llamadas | Tokens de salida |
| --- | --- | --- |
| Correlación (2 pasadas) | hasta 6 | 3 000 |
| Guion de exposición | 1 | 2 800 |
| Preguntas de repaso | 1 | 2 800 |
| Resumen, ideas, glosario | 1 | 2 400 |
| Ficha por obra (paso 1) | 1 por libro | 900 |

## Enrutamiento

`IntentRouter.route(frase)` devuelve una herramienta cuando reconoce una orden:

- **Correlación**: compara, compárame, correlaciona, contrasta, cruza, diferencias entre, coinciden,
  contradicen, intersección.
- **Resumen**: resumen, resume, sintetiza, de qué trata, de qué habla.
- **Ideas nuevas**: ideas nuevas, dame/genera ideas, propuestas, hipótesis.
- **Guion**: exposición, presentación, guion, diapositivas, exponer.
- **Glosario**: glosario, términos clave, vocabulario.
- **Preguntas**: preguntas de repaso/examen, hazme/genera preguntas, cuestionario.

La comparación se hace sin acentos y devuelve `null` cuando no hay orden clara: eso mantiene la
charla libre intacta. Si un texto ambiguo cae en la herramienta equivocada, los botones de la mesa
siempre son explícitos.

## Añadir una herramienta

1. Agrega la tarea en `core/model/Studio.kt` (`StudioTasks`).
2. Añade su presupuesto en `ToolRegistry.default()`.
3. Si debe reconocerse en lenguaje natural, agrega sus palabras clave en `IntentRouter.RULES`.
4. Añade un test del enrutador y del presupuesto.

## Límites

- El enrutador es **determinista**: no planifica ni encadena herramientas por sí solo. Un planificador
  generativo queda para cuando las herramientas individuales tengan métricas del corpus.
- No hay confirmación de costo antes de ejecutar; el presupuesto es un tope, no una pregunta.
- `ModelGateway` sólo tiene un proveedor; el registro de proveedores (OpenAI, OpenRouter, Ollama)
  es el siguiente paso natural.
