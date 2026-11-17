#!/usr/bin/env python3
"""Prueba de generación real sobre los contextos recuperados por la app.

Uso:
    $env:NAHUA_API_KEY = "..."            # PowerShell
    python docs/eval/live_eval.py

Lee app/build/eval/contexts.json (lo escribe CorpusBenchmarkTest) y manda cada
contexto al endpoint configurado con el mismo system prompt del estudio.
Imprime el número de citas y un extracto de la respuesta. No guarda la key.
"""
import json
import os
import re
import sys
import urllib.request

KEY = os.environ.get("NAHUA_API_KEY", "").strip()
BASE = os.environ.get("NAHUA_BASE_URL", "https://opencode.ai/zen/go/v1").rstrip("/")
MODEL = os.environ.get("NAHUA_MODEL", "deepseek-v4.1-flash")
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

SYSTEM = (
    "Eres Nahuanotoka, el asistente de estudio de un estudiante mexicano. "
    "Respondes SIEMPRE en español claro, directo y sin relleno. "
    "(1) usas únicamente el contexto entregado; (2) cada afirmación tomada del contexto lleva su cita "
    "[Documento, p. N]; (3) si algo no está en el contexto lo dices; (4) no inventas cifras ni citas; "
    "(5) separas fuente de inferencia; (6) Markdown con encabezados, listas y tablas."
)
CORRELATE = (
    "\nEn esta consulta compara, no resumas: la tabla por eje y por obra es obligatoria, "
    "cada celda debe llevar su cita y las obras sin postura se marcan 'no se pronuncia'."
)

QUESTIONS = {
    "C1": "¿Qué significa el territorio cuerpo-tierra?",
    "C2": "¿Por qué la crueldad funciona como pedagogía del poder?",
    "C3": "¿Qué se reclama con la campaña por el salario doméstico?",
    "C4": "¿Cómo se articulan patriarcado, colonialidad y comunidad en estos textos?",
    "C5": "¿Dónde empieza el capítulo sobre colonialidad y patriarcado moderno?",
    "C6": "¿Cuál es la fórmula química de la fotosíntesis?",
    "C7": "¿Por qué la huelga es central en la lucha?",
}


def ask(case_id, blocks, max_tokens=4000):
    question = QUESTIONS[case_id]
    user = "Contexto extraído de mis documentos:\n" + "\n\n".join(blocks)
    user += "\n\nPregunta: " + question + "\n\nResponde citando [Documento, p. N] cuando uses el contexto."
    extra = CORRELATE if case_id == "C4" else ""
    payload = json.dumps({
        "model": MODEL,
        "messages": [{"role": "system", "content": SYSTEM + extra}, {"role": "user", "content": user}],
        "max_tokens": max_tokens,
    }).encode()
    request = urllib.request.Request(BASE + "/chat/completions", data=payload, method="POST")
    request.add_header("Authorization", "Bearer " + KEY)
    request.add_header("Content-Type", "application/json")
    request.add_header("User-Agent", UA)
    request.add_header("x-opencode-session", "nahua-eval-" + case_id)
    with urllib.request.urlopen(request, timeout=300) as response:
        data = json.loads(response.read().decode())
    choice = data["choices"][0]
    answer = choice["message"].get("content") or ""
    cites = re.findall(r"\[[^\]]+,\s*p\.?\s*\d+\]", answer)
    print("=" * 78)
    print(case_id, "| finish:", choice.get("finish_reason"), "| citas:", len(cites),
          "| tokens:", data["usage"]["prompt_tokens"], "/", data["usage"]["completion_tokens"])
    print(answer[:900].replace("\n", " "))
    return answer


def main():
    if not KEY:
        print("Falta NAHUA_API_KEY", file=sys.stderr)
        return 1
    path = "app/build/eval/contexts.json"
    cases = {c["id"]: c for c in json.load(open(path, encoding="utf-8"))["cases"]}
    for case_id in ("C1", "C2", "C7", "C4"):
        ask(case_id, cases[case_id]["blocks"], 8000 if case_id == "C4" else 4000)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
