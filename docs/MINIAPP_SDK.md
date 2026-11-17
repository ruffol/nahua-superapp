# Nahua Mini App SDK (v1.2)

Una mini app es un bundle web (HTML/CSS/JS) que vive en `app/src/main/assets/miniapps/<id>/` y se
declara en `app/src/main/assets/miniapps/index.json`. La app anfitriona la carga en un WebView
aislado y le expone una API pequeña para identidad, pagos, ubicación en vivo, valoración de
objetos, avisos a la bandeja nativa y navegación.

## Manifiesto

```json
{
  "id": "ride",
  "name": "Nahua Ride",
  "tagline": "Muévete por la ciudad",
  "version": "1.0.0",
  "entry": "miniapps/ride/index.html",
  "accent": "#FF6B4A",
  "permissions": ["wallet.pay", "user.profile"]
}
```

| Campo | Requerido | Descripción |
| --- | --- | --- |
| `id` | sí | Identificador único; debe coincidir con la carpeta |
| `name` | sí | Nombre visible en la barra de la mini app |
| `tagline` | no | Texto corto para el catálogo |
| `version` | no | Semver mostrado en el chrome de la mini app |
| `entry` | sí | Ruta relativa a `assets/` del HTML de entrada |
| `accent` | no | Color de marca en formato `#RRGGBB` |
| `permissions` | no | Scopes declarativos (`wallet.pay`, `user.profile`, `storage.local`, `inbox.notify`, `location`, `map.tiles`): documentan lo que usa el bundle |

## API de JavaScript

Incluye el SDK antes de tu código:

```html
<script src="../nahua-sdk.js"></script>
```

### `Nahua.user()`

Devuelve el perfil de la sesión:

```js
const user = Nahua.user();
// { name, handle, phone, tier, memberSince, initials }
```

### `Nahua.valuate(item)`

Valora un objeto del bazar. Devuelve el valor estimado, el rango, la confianza y el desglose del
cálculo. Es **síncrona** a propósito: el formulario de publicación recalcula en cada tecla. La
aritmética vive en el anfitrión (`BarterValuator` en Kotlin), no en la página.

```js
const valor = Nahua.valuate({
  referenceCents: 650000,   // lo que cuesta nuevo, en centavos
  category: 'BIKES',        // PHONES, COMPUTERS, TOOLS, FURNITURE, …
  condition: 'GOOD',        // NEW, LIKE_NEW, GOOD, WORN, FOR_PARTS
  ageMonths: 36,
  complete: true,           // ¿conserva caja y accesorios?
  hasReceipt: false
});
// { estimatedCents: 242500, lowCents: 213500, highCents: 271500,
//   barterMinCents: 206000, confidence: 'alta',
//   factors: [{ label: 'Reventa de bicis', percent: -42, tone: 'negative' }, …] }
```

Devuelve `null` cuando la página corre fuera de la app; la mini app usa entonces su estimación local
(`bazar.js`) y sigue funcionando en el navegador.

### `Nahua.compare(mine, theirs)`

Compara el objeto que das con el que recibes y decide si el trueque conviene.

```js
const balance = Nahua.compare(
  { referenceCents: 190000, category: 'SPORTS', condition: 'LIKE_NEW', ageMonths: 8 },
  { referenceCents: 650000, category: 'BIKES', condition: 'GOOD', ageMonths: 36 }
);
// { verdict: 'favorable', verdictLabel: 'A tu favor', deltaCents: 139000,
//   ratioPercent: 134, cashTopUpCents: 0,
//   message: 'Recibes $1,390.00 más de valor (134 %). Te conviene truequear.' }
```

`verdict` es `favorable`, `justo` o `desfavorable`. Cuando el trato sale corto, `cashTopUpCents`
trae la compensación sugerida en efectivo (redondeada a $50) para emparejar el valor.

### `Nahua.announce({ title, body })`

Deja un aviso en la bandeja nativa (**Avisos**). El bazar la usa para notificar que a alguien le
interesa truequear un objeto publicado. Requiere el permiso `inbox.notify`.

```js
Nahua.announce({
  title: 'Ana Nahua quiere truequear tu Bicicleta rodada 26',
  body: 'Te ofrece Mochila de senderismo 40 L ($1,035). Recibes $1,390 más de valor.'
}).then(() => Nahua.toast('Propuesta enviada'));
```

Los avisos entran sin leer, con su propio tipo (*Bazar*) y saltan en el contador del inicio; el texto
se recorta a 90 caracteres de título y 240 de cuerpo.

### `Nahua.pay(options)`

Cobra a la cartera del usuario. Siempre abre una confirmación nativa.

```js
Nahua.pay({
  amount: 95.5,          // pesos, se convierte a centavos en el host
  concept: 'Pozole rojo',
  merchant: 'Cocina de Doña Meche'
})
  .then(result => {
    console.log(result.transactionId, result.balanceCents);
  })
  .catch(error => {
    // error.error: insufficient_funds | cancelled | invalid_amount
  });
```

### `Nahua.location()`

Pide una ubicación puntual. El host se encarga del permiso la primera vez.

```js
Nahua.location()
  .then(pos => {
    console.log(pos.lat, pos.lng, pos.accuracy, pos.speed, pos.bearing, pos.altitude);
  })
  .catch(error => {
    // error.error: permission_denied | unavailable
  });
```

### `Nahua.watchLocation(handler, options)`

Inicia un flujo continuo de GPS. Devuelve una función para detener **ese** watcher. El host
mantiene el `LocationManager` activo solo mientras haya listeners; al cerrar la mini app o
pausarla, el seguimiento se cancela.

```js
const stop = Nahua.watchLocation(fix => {
  console.log(fix.lat, fix.lng, fix.accuracy, fix.speed, fix.bearing, fix.altitude);
}, { intervalMs: 1500 });

const offError = Nahua.onLocationError(error => {
  // permission_denied | unavailable
});

// más tarde
stop();
offError();
Nahua.stopLocation(); // detiene todos los watchers de la página
```

Campos del fix: `lat`, `lng`, `accuracy` (m), `speed` (m/s), `bearing` (grados desde el norte),
`altitude` (m), `timestamp` (ms) y `provider`. Los campos de velocidad, rumbo y altitud pueden
faltar si el proveedor no los reporta.

### `Nahua.openDirections(lat, lng, label)`

Abre la app de mapas del sistema con el destino puesto. En Android usa un intent `geo:`; si no hay
ninguna app instalada, el host muestra un aviso. La ruta giro a giro la dibuja esa app: la mini app
solo calcula distancia, rumbo y ETA aproximados.

```js
Nahua.openDirections(19.2436, -103.7246, 'Jardín Libertad');
```

### `Nahua.copy(text)`

Copia al portapapeles del sistema y devuelve una promesa. Úsalo para “copiar coordenadas”.

```js
Nahua.copy('19.243600, -103.725000')
  .then(() => Nahua.toast('Coordenadas copiadas'))
  .catch(() => Nahua.toast('No se pudo copiar'));
```

### `Nahua.toast(message)`

Muestra un snackbar nativo.

### `Nahua.close()`

Cierra la mini app y vuelve al inicio.

## Flujo de un pago

```
JS                     Host (Kotlin)                 UI
 │ Nahua.pay({...})         │                          │
 ├─────────────────────────►│ MiniAppBridge.pay        │
 │                          ├─────────────────────────►│ Hoja de confirmación
 │                          │                          │ (saldo, monto)
 │                          │◄─────────────────────────┤ Confirmar
 │                          │ WalletRepository.pay     │
 │                          │◄──────── resultado ──────┤
 │◄── _resolve(id, result) ─┤                          │
 │ .then / .catch           │                          │
```

Reglas:

- El monto se valida en Kotlin; un `amount` inválido se rechaza sin abrir UI.
- Sin confirmación explícita no hay débito.
- El historial guarda concepto, comercio, monto y timestamp; el `transactionId` es estable.
- El puente **no** expone el saldo ni datos de otros usuarios a la página.

## Flujo de un trueque

```
JS (bazar.js)                 Host (Kotlin)                        UI / almacenamiento
 │ Publicar: cada tecla          │                                    │
 ├── Nahua.valuate(item) ───────►│ MiniAppBridge.valuate              │
 │◄── valor + desglose ──────────┤ BarterValuator.valuate             │
 │                               │                                    │
 │ Truquear                      │                                    │
 ├── Nahua.compare(mío, suyo) ──►│ BarterValuator.compare             │
 │◄── A tu favor / Pareja / … ───┤                                    │
 ├── Nahua.announce({...}) ─────►│ MiniAppBridge.announceBarter       │
 │                               ├──► InboxRepository.push ──────────►│ Avisos + contador
 │                               │    NoticeKind.MARKET               │
 │ guarda la propuesta en localStorage                                │
 └── abre el trato con el perfil del interesado
```

Reglas:

- La valoración y la comparación se calculan **en Kotlin**; la página sólo pinta el resultado. Sin
  anfitrión (navegador) el bazar usa su espejo local y lo advierte.
- El trueque sólo existe si quien publica lo habilitó **y** el objeto quedó valorado.
- Avisar no cobra ni mueve saldo: sólo deja el aviso y la propuesta. El efectivo de una compensación
  se acuerda en el trato, no se cobra automáticamente.

## Ubicación y privacidad

- `requestLocation` y `startLocationUpdates` comprueban el permiso en el host; la página nunca
  recibe datos de otras apps ni puede activar el GPS sin que el usuario lo autorice.
- El flujo se detiene al cerrar la mini app, al ocultar la pantalla o cuando la página llama a
  `stopLocation`; no hay servicios en segundo plano.
- La posición se queda en el teléfono. Solo se comparte el destino cuando el usuario pulsa
  explícitamente **Navegar**.
- En el manifiesto, `location` y `map.tiles` son scopes declarativos; el puente no concede permisos
  adicionales por declararlos.

## Seguridad

- `WebViewAssetLoader` sirve los assets desde `https://appassets.androidplatform.net`, así que el
  WebView no tiene acceso a `file://` ni a contenido local arbitrario.
- `settings.allowFileAccess = false` y `allowContentAccess = false`.
- Navegación externa: cualquier `http(s)` fuera del origen se abre en el navegador del sistema.
- El puente solo publica métodos anotados con `@JavascriptInterface`; el resto de la API de Android
  queda fuera del alcance de la página.
- En producción: firmar el bundle, validar el manifiesto contra una lista blanca y limitar los
  permisos por scope.

## Desarrollo local

1. Crea `app/src/main/assets/miniapps/mi-app/index.html`.
2. Agrega su entrada a `index.json`.
3. Añade el `Service` correspondiente en `ServiceCatalog` con `ServiceTarget.MiniApp("mi-app")`.
4. Corre `./gradlew installDebug` y ábrela desde el hub.
5. Agrega un test de integridad: `ServiceCatalogTest` verifica que el bundle exista.

Consejo: mantén cada mini app autocontenida (CSS y JS propios) para que pueda publicarse como
bundle independiente el día que exista el servidor de mini-apps.