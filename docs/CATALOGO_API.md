# Catálogo oficial del Bazar/Trueque

Las **organizaciones oficiales** viven dentro de la mini-app `bazar` (pestaña **Tiendas** y banners
en la portada). Sus productos tienen stock permanente, se venden con la cartera y el catálogo se
sincroniza por **deltas**: la foto completa se descarga una sola vez y después sólo viajan altas,
bajas, cambios de precio y de stock.

La app conserva **todas las versiones** en memoria y muestra:
- si el catálogo está **al día** o **sin actualizar** (y desde cuándo),
- la **actividad** de cada versión (precio anterior → nuevo, stock anterior → nuevo, altas y bajas),
- el **stock** restante por producto y la etiqueta *Agotado*.

El contrato completo para conectar el servidor —endpoints, modelo, auditoría, frescura, reportes y
antifraude— está en **[Catalogo-Servidor-Requisitos.pdf](Catalogo-Servidor-Requisitos.pdf)**
(fuente editable: [catalogo-requisitos.html](catalogo-requisitos.html)).

## Servidor en producción

`https://tienda.tlalchichi.xyz` — corre en el teléfono del dueño (Node + SQLite + tunnel) y publica
también la tienda en `/bazar/`. Verificado en vivo el 19/11/2026:

| Ruta | Respuesta real |
| --- | --- |
| `/v1/health` | `ok`, `integrity: true`, `signed: true`, 2 tiendas, 12 artículos |
| `/v1/catalog/head` | `{version:1, serverTime, generatedAt, validUntil, chainHash, stores:2, products:12}` |
| `/v1/catalog?full=1` | `{version, serverTime, generatedAt, stores, products}` — **no repite `validUntil`** |
| `/v1/catalog/delta?since=1` | `{to:1, serverTime, patches:[]}` |
| `POST /v1/orders` con precio viejo | `409 price_changed` |
| `POST /v1/orders` sin stock | `409 out_of_stock` |
| `POST /v1/reports` sin `reporter` | `400 reporter_required` |

La app toma la vigencia de `/head` y la guarda junto con la foto. **Nadie más hace HTTP**: la WebView
responde 404 a cualquier request externo, así que `CatalogApi.kt` (Kotlin) es el cliente y la página
recibe el JSON por el puente (`Nahua.catalogHead/Full/Delta/Order/Report`).

## Endpoints

| Método | Ruta | Uso |
| --- | --- | --- |
| `GET` | `/v1/catalog/head` | versión vigente y `serverTime` |
| `GET` | `/v1/catalog?full=1` | foto completa (sólo primera instalación) |
| `GET` | `/v1/catalog/delta?since=N` | parches con versión > N |
| `GET` | `/v1/catalog/changes?since=N` | actividad legible (opcional) |
| `POST` | `/v1/orders` | cobro y descuento de stock atómico |
| `POST` | `/v1/reports` | reportes de artículos o usuarios |

El adaptador actual (`bazar.js`, objeto `SOURCE`) sirve la foto y los parches incluidos en el bundle
para que todo funcione sin red. Cambiarlo por `fetch` no toca la UI.

## Modelo

```jsonc
// Producto
{
  "id": "c1",
  "storeId": "coop",
  "title": "Taza de cerámica del campus",
  "priceCents": 6500,     // centavos enteros
  "stock": 8,             // 0 = agotado, no borrado
  "emoji": "☕",
  "tag": "Fijado",
  "createdAt": 1793000000000
}

// Parche (inmutable, versión consecutiva)
{
  "version": 5,
  "at": 1795600000000,
  "added":   [ { "id": "c9", "storeId": "coop", "title": "Pluma", "priceCents": 1500, "stock": 40, "emoji": "🖊", "tag": "Nuevo", "createdAt": 1795600000000 } ],
  "removed": [ "c4" ],
  "updated": [ { "id": "c1", "priceCents": 7000, "stock": 3 } ]
}
```

Reglas duras: `updated` lleva sólo los campos cambiados; los parches no se editan; un id retirado no
se reutiliza; `since=N` es idempotente.

## Cliente: `MercadoCatalogo` (`bazar/catalogo.js`)

| Método | Qué hace |
| --- | --- |
| `installed()` | ¿hay foto inicial en memoria? |
| `install()` | foto del bundle (modo demostración, sin red) |
| `installFrom(snapshot, opts)` | instala una foto ya obtenida (servidor) con su vigencia |
| `applyRemote(payload)` | aplica el `delta` del servidor; devuelve el resumen |
| `markChecked(validUntil)` | el servidor no tenía cambios: registra la consulta |
| `head()` | versión local aplicada |
| `total()` | productos del catálogo actual |
| `snapshot(version?)` | foto reconstruida de esa versión |
| `sync()` | pide el delta al bundle y lo aplica (sin servidor) |
| `history()` | versiones conocidas con altas/bajas/cambios |
| `audit()` | actividad con valor anterior y nuevo por producto |
| `syncedAt()` / `checkedAt()` | última sincronización / última consulta |
| `origin()` | `remote` (servidor) o `demo` (bundle) |
| `validUntil()` | vigencia declarada por el servidor |
| `pending()` | qué traería la próxima sincronización |

`sync()` devuelve `{ kind: 'delta' | 'full' | 'current', from, to, added, removed, updated, items, completeCatalogue }`.
Con `kind: 'current'` no se descargó nada.

## Frescura

La app marca el catálogo como **sin actualizar** si hay una versión más nueva en el servidor o si la
última sincronización tiene más de **24 h** (`STALE_MS`). En ese estado:

- la portada y la tienda muestran el aviso ámbar,
- cada tarjeta advierte «puede faltar stock»,
- la compra pide una segunda confirmación antes de abrir el pago.

## Actividad y reportes

La actividad se deriva comparando las fotos reconstruidas de cada parche, así que no depende de un
campo extra del servidor; si `/changes` existe, se puede usar para mostrar también el actor y el
motivo. Los **reportes** se guardan en `data.reports` (objetivo, motivo, nota, fecha) y se envían a la
bandeja con `Nahua.announce`; el endpoint `POST /v1/reports` los recibe cuando exista backend.

## Pruebas

```bash
node scripts/test-bazar-catalogo.js
```

Cubre: instalación completa única, delta con altas/bajas/campos, stock conservado, reconstrucción de
versiones viejas con productos retirados, actividad con precio/stock anterior y nuevo, y persistencia
entre aperturas.
