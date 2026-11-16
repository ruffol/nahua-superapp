# Nahua Mini App SDK (v1)

Una mini app es un bundle web (HTML/CSS/JS) que vive en `app/src/main/assets/miniapps/<id>/` y se
declara en `app/src/main/assets/miniapps/index.json`. La app anfitriona la carga en un WebView
aislado y le expone una API pequeña para identidad, pagos y navegación.

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
| `permissions` | no | Scopes declarativos (informativo en v1) |

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
