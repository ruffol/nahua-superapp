# Corpus de evaluación RAG (NA-003)

Objetivo: medir si un cambio en el motor **mejora** la recuperación sin romper los casos que hoy funcionan.
El plan lo exige antes de tocar `ContextBuilder` (NA-004), el contrato de evidencia (NA-005) o la caché (NA-006).

## Estado

- **Fixtures:** incluidas en `docs/eval/fixtures/` (5 obras · 535 páginas · 6.1 MB):
  Cabnal 2010, Guzmán y Triana sobre Julieta Paredes, Segato *La guerra contra las mujeres*,
  *Feminismo descolonial* y Federici *Revolución en punto cero*.
- **Casos:** `docs/eval/cases.json` con 7 casos (C1–C7) y sus páginas esperadas.
- **Automatizado:** `RealCorpusTest` ejecuta C1, C2, C3 y C6 sobre una página real de cada libro
  (texto en `app/src/test/resources/eval/`); el resto requiere correr la app con la IA configurada.
- **Resultados:** pendientes de una corrida completa en dispositivo.

## Casos mínimos

| ID | Documento(s) | Consulta | Evidencia esperada | Qué valida |
| --- | --- | --- | --- | --- |
| C1 | Un PDF con texto | “¿Cuándo se nacionalizó el petróleo?” | Página con la frase exacta | Caso literal: BM25 debe acertar |
| C2 | El mismo PDF | “¿Por qué terminó el porfiriato?” con la respuesta redactada como “causas del fin del régimen” | Pasajes equivalentes | Paráfrasis: documentar el límite de BM25 |
| C3 | Libro de 300+ páginas | “¿Qué dice el capítulo 9 sobre la deuda?” | Frase ubicada en la segunda mitad de un bloque | Recorte fijo: hoy puede perderse |
| C4 | Cuatro libros | “¿En qué coinciden y en qué se contradicen?” | Al menos 1 fragmento por libro | Balanceo y cobertura |
| C5 | PDF con numeración impresa ≠ PDF | “Cita la página 42 impresa” | Página impresa vs índice del PDF | Trazabilidad de cita |
| C6 | Libro sin relación con la consulta | “¿Qué dice sobre física cuántica?” | Vacío / abstención | Tasa de abstención correcta |

## Formato de `cases.json`

```json
{
  "version": 1,
  "cases": [
    {
      "id": "C1",
      "documents": ["fixtures/historia-economica.pdf"],
      "question": "¿Cuándo se nacionalizó el petróleo?",
      "taskId": null,
      "expected": {
        "mode": "busqueda",
        "minSources": 1,
        "contains": ["1938"],
        "pages": [8],
        "maxRankOfFirstHit": 1
      },
      "notes": "Cita textual en la página 8."
    }
  ]
}
```

## Cómo evaluar (manual, por ahora)

1. Coloca los archivos en `docs/eval/fixtures/`.
2. Crea una mesa en la app e importa los documentos del caso.
3. Haz la consulta y anota: modo usado, fuentes (documento/página), si la respuesta contiene lo esperado.
4. Registra el resultado en `docs/eval/results-<fecha>.md` con la versión de la app.
5. Un cambio se acepta sólo si **mantiene C1, C4 y C5** y mejora al menos uno de C2, C3 o C6.

## Nota sobre ventanas

Desde 0.8.1 el contexto usa hasta dos ventanas de oraciones por fragmento. Si un caso falla porque la
evidencia está en una región lejana del mismo fragmento, revisa primero `ContextBuilder.windows`
antes de bajar el tamaño de bloque.

## Métricas propuestas (cuando haya automatización)

- **Recall@k** por caso (¿la página correcta está entre las fuentes?).
- **MRR** del primer acierto.
- **Precisión de citas**: porcentaje de citas que resuelven a un fragmento existente.
- **Abstención correcta**: casos sin evidencia que no inventan respuesta.
- **Costo**: tokens de entrada por consulta en cada caso.