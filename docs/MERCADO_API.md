# API del catálogo del Mercado

El **Mercado** (mini-app `mercado`) muestra los productos de las tiendas oficiales del campus. El
catálogo cambia a diario: altas, bajas y cambios de precio. Descargarlo completo en cada apertura
gasta datos y hace perder la memoria de lo que había antes, así que la app funciona con un
**catálogo versionado**:

1. La primera instalación descarga la foto completa **una sola vez**.
2. Cada sincronización posterior pide **sólo lo agregado, lo retirado y los campos que cambiaron**.
3. Todas las versiones quedan guardadas en memoria y se pueden consultar (`Catálogo → Versiones`),
   incluidos los productos que después se retiraron.

La implementación del cliente está en `app/src/main/assets/miniapps/mercado/catalogo.js` y no
depende de la red: recibe un `source` con la misma forma que los endpoints de abajo. El adaptador
local (`mercado.js`) sirve la foto y los parches incluidos en el bundle para que la app funcione
sin conexión; cuando exista el backend, `source` se cambia por `fetch` sin tocar la UI.

## Modelo

```jsonc
// Tienda
{
  "id": "coop",
  "name": "Cooperativa del Campus",
  "short": "Cooperativa",
  "tagline": "Útiles, tazas y termos para la semana",
  "accent": "#2F6B4F",
  "deep": "#1E4A36",
  "emoji": "🏫",
  "offer": "10 % en útiles al mostrar tu credencial",
  "since": "2019",
  "pinned": ["c1"]            // productos que la tienda fija para impulsar la venta
}

// Producto
{
  "id": "c1",
  "storeId": "coop",
  "title": "Taza de cerámica del campus",
  "priceCents": 6500,
  "emoji": "☕",
  "tag": "Fijado",
  "createdAt": 100             // orden de llegada; la tienda muestra lo último primero
}
```

Todo el dinero va en **centavos enteros**. `createdAt` es un entero creciente (epoch en ms en
producción); el cliente ordena por ese campo, nunca por el orden del array.

## Endpoints

### `GET /catalog/head`

```json
{ "version": 4 }
```

Sirve para saber si hay algo nuevo antes de pedir el delta.

### `GET /catalog?full=1`

Foto completa. Sólo se llama cuando el dispositivo no tiene catálogo (primera instalación o
almacenamiento borrado).

```json
{ "version": 1, "stores": [ ... ], "products": [ ... ] }
```

### `GET /catalog/delta?since=N`

Devuelve **los parches** con versión mayor a `N`, en orden y sin huecos. Si no hay cambios:
`{ "to": N, "patches": [] }`.

```json
{
  "to": 4,
  "patches": [
    {
      "version": 2,
      "at": 1730000000000,
      "added":   [ { "id": "c7", "storeId": "coop", "title": "Sudadera del campus", "priceCents": 42000, "emoji": "🧥", "tag": "Nuevo", "createdAt": 130 } ],
      "removed": [ "c5" ],
      "updated": [ { "id": "k1", "priceCents": 13000 } ]
    },
    { "version": 3, "at": 1730086400000, "added": [ ... ], "removed": [ "k4" ], "updated": [] },
    { "version": 4, "at": 1730172800000, "added": [ ... ], "removed": [ "c6" ], "updated": [ { "id": "t2", "priceCents": 58000 } ] }
  ]
}
```

Reglas del parche:

- `updated` lleva **sólo los campos que cambiaron** más el `id`. El cliente conserva el resto.
- `removed` son ids; un producto retirado sigue existiendo en las versiones anteriores.
- Los parches son **inmutables y consecutivos**. Un parche publicado no se edita: un cambio nuevo
  es un parche nuevo con versión mayor.
- `added` puede reintroducir un id retirado? **No.** Si un producto vuelve, recibe un id nuevo, para
  que las vistas históricas no se contradigan.

## Cliente: `MercadoCatalogo`

`catalogo.js` expone `MercadoCatalogo.create({ storage, source, key })`:

| Método | Qué hace |
| --- | --- |
| `installed()` | ¿hay una foto inicial en memoria? |
| `head()` | versión local aplicada |
| `total()` | productos del catálogo actual |
| `snapshot(version?)` | foto reconstruida de esa versión (sin argumento: la actual) |
| `install()` | descarga la foto completa (sólo la primera vez) |
| `sync()` | pide el delta desde `head()` y lo aplica; devuelve el resumen |
| `history()` | versiones conocidas con altas/bajas/cambios de cada una |
| `pending()` | qué traería la próxima sincronización, sin aplicarla |

`sync()` devuelve:

```jsonc
// primera vez
{ "kind": "full", "from": 0, "to": 1, "added": 22, "removed": 0, "updated": 0, "items": 22, "completeCatalogue": true }
// actualización
{ "kind": "delta", "from": 1, "to": 4, "added": 6, "removed": 3, "updated": 2, "items": 11, "completeCatalogue": false }
// sin cambios
{ "kind": "current", "from": 4, "to": 4, "added": 0, "removed": 0, "updated": 0, "items": 0 }
```

`completeCatalogue` es la señal que la UI usa para decir «esta vez sí se bajó todo» en lugar de
«11 fichas en lugar de 28». Si el servidor devuelve un parche con `version` menor o igual a
`head()`, se descarta: la sincronización es idempotente.

## Almacenamiento

Un solo registro en `localStorage`:

```jsonc
{
  "base": { "version": 1, "stores": [...], "products": [...], "at": 1730000000000 },
  "patches": [ { "version": 2, ... }, ... ],
  "head": 4,
  "installedAt": 1730000000000,
  "log": [ { "kind": "full", "version": 1, "added": 22 }, { "kind": "delta", "version": 4, "items": 11 } ]
}
```

Guardar la foto inicial más los parches (en vez de una foto por versión) mantiene el uso de
almacenamiento bajo: consultar la v1 es reconstruir con `base` y aplicar los parches hasta v1 (es
decir, ninguno).

## Pruebas

```bash
node scripts/test-mercado-catalogo.js
```

Comprueba que la primera sincronización es la descarga completa, que la segunda trae sólo
altas/bajas/cambios, que una versión vieja conserva productos retirados y precios anteriores, que
consultar una versión no muta la memoria y que el estado persiste al reabrir.
