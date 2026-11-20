/*
 * Prueba del catálogo versionado del bazar (sin WebView, con node).
 *   node scripts/test-bazar-catalogo.js
 *
 * Valida lo que el requisito pide y lo que un refactor rompe en silencio:
 *   1. La primera instalación es la única descarga completa.
 *   2. Sincronizar trae sólo altas/bajas/cambios, nunca el catálogo entero.
 *   3. El catálogo viejo queda consultable, incluidos los productos retirados.
 *   4. El stock viaja en los parches y se conserva si no cambió.
 *   5. La actividad (auditoría) muestra precio anterior → nuevo y stock anterior → nuevo.
 *   6. `applyDelta` no muta la foto anterior.
 */
'use strict';

var path = require('path');
var Catalogo = require(path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'miniapps', 'bazar', 'catalogo.js'));

var failures = 0;

function check(name, condition, detail) {
  if (condition) {
    console.log('  ok  ' + name);
  } else {
    failures += 1;
    console.log('  FAIL ' + name + (detail ? ' → ' + detail : ''));
  }
}

function MemoryStorage() {
  this.map = {};
}
MemoryStorage.prototype.getItem = function (key) {
  return Object.prototype.hasOwnProperty.call(this.map, key) ? this.map[key] : null;
};
MemoryStorage.prototype.setItem = function (key, value) {
  this.map[key] = String(value);
};
MemoryStorage.prototype.removeItem = function (key) {
  delete this.map[key];
};

function product(id, title, priceCents, stock, createdAt) {
  return {
    id: id, storeId: 's1', title: title, priceCents: priceCents,
    stock: stock, emoji: '🧺', tag: 'nuevo', createdAt: createdAt
  };
}

var base = {
  version: 1,
  stores: [{ id: 's1', name: 'Tienda Uno', accent: '#2F6B4F', pinned: ['p1'] }],
  products: [
    product('p1', 'Taza', 5000, 4, 1),
    product('p2', 'Blusa', 8000, 6, 2),
    product('p3', 'Conserva', 3000, 9, 3),
    product('p4', 'Guitarra', 12000, 1, 4),
    product('p5', 'Maceta', 2000, 12, 5)
  ]
};

var patches = [
  {
    version: 2,
    at: 100,
    added: [product('p6', 'Rebozo', 9000, 3, 6)],
    removed: ['p3'],
    updated: [{ id: 'p1', priceCents: 5500, stock: 2 }]
  },
  {
    version: 3,
    at: 200,
    added: [product('p7', 'Café', 4000, 15, 7)],
    removed: [],
    updated: [{ id: 'p2', stock: 0 }]
  },
  {
    version: 4,
    at: 300,
    added: [product('p8', 'Semillas', 2500, 8, 8)],
    removed: ['p2'],
    updated: [{ id: 'p4', stock: 0 }]
  }
];

var source = {
  base: function () { return JSON.parse(JSON.stringify(base)); },
  head: function () { return 4; },
  delta: function (since) {
    var list = patches.filter(function (patch) { return patch.version > since; });
    return { to: 4, patches: JSON.parse(JSON.stringify(list)) };
  }
};

var storage = new MemoryStorage();
var catalogo = Catalogo.create({ storage: storage, source: source });

console.log('catálogo versionado del bazar');

var install = catalogo.sync();
check('la primera sincronización es la descarga completa', install.kind === 'full' && install.completeCatalogue === true);
check('instala la foto base (5 fichas)', install.added === 5 && install.to === 1, JSON.stringify(install));
check('el head local queda en v1', catalogo.head() === 1);
check('snapshot() actual trae 5 productos', catalogo.snapshot().products.length === 5);
check('guarda cuándo se sincronizó', typeof catalogo.syncedAt() === 'number' && catalogo.syncedAt() > 0);

var delta = catalogo.sync();
check('la segunda sincronización es un delta', delta.kind === 'delta' && delta.completeCatalogue === false, JSON.stringify(delta));
check('el delta abarca v1 → v4', delta.from === 1 && delta.to === 4);
check('sólo descarga 3 altas', delta.added === 3, String(delta.added));
check('sólo descarga 2 bajas', delta.removed === 2, String(delta.removed));
check('sólo descarga 3 campos cambiados', delta.updated === 3, String(delta.updated));
check('fueron 8 fichas (altas + bajas + campos), no el catálogo entero', delta.items === 8, String(delta.items));

var current = catalogo.snapshot();
var currentIds = current.products.map(function (p) { return p.id; }).sort();
check('el catálogo actual tiene 6 productos', current.products.length === 6, String(current.products.length));
check('p3 y p2 quedaron retirados', currentIds.indexOf('p3') < 0 && currentIds.indexOf('p2') < 0, currentIds.join(','));
check('p6, p7 y p8 están agregados', currentIds.indexOf('p6') >= 0 && currentIds.indexOf('p7') >= 0 && currentIds.indexOf('p8') >= 0);
check('el precio de p1 cambió a 5500', current.products.filter(function (p) { return p.id === 'p1'; })[0].priceCents === 5500);
check('el stock de p1 cambió a 2', current.products.filter(function (p) { return p.id === 'p1'; })[0].stock === 2);
check('el stock de p4 quedó en 0', current.products.filter(function (p) { return p.id === 'p4'; })[0].stock === 0);
check('el stock de p5 se conserva en 12', current.products.filter(function (p) { return p.id === 'p5'; })[0].stock === 12);

var old = catalogo.snapshot(1);
check('la versión 1 se reconstruye con 5 productos', old.products.length === 5, String(old.products.length));
check('la versión 1 conserva el producto retirado p3', old.products.some(function (p) { return p.id === 'p3'; }));
check('la versión 1 conserva el precio viejo de p1', old.products.filter(function (p) { return p.id === 'p1'; })[0].priceCents === 5000);
check('la versión 1 conserva el stock viejo de p1', old.products.filter(function (p) { return p.id === 'p1'; })[0].stock === 4);

var v2 = catalogo.snapshot(2);
check('la versión 2 refleja su propio parche (5 fichas)', v2.products.length === 5, String(v2.products.length));
check('la versión 2 ya no tiene p3', !v2.products.some(function (p) { return p.id === 'p3'; }));
check('la versión 2 todavía no tiene p7', !v2.products.some(function (p) { return p.id === 'p7'; }));

var history = catalogo.history();
check('el historial lista las 4 versiones', history.length === 4, JSON.stringify(history.map(function (h) { return h.version; })));
check('la primera entrada es la foto completa', history[0].kind === 'full' && history[0].version === 1);

var audit = catalogo.audit();
check('la actividad lista las 3 actualizaciones', audit.length === 3, String(audit.length));
check('la actividad viene de la más reciente a la más vieja', audit[0].version === 4 && audit[2].version === 2,
  audit.map(function (e) { return e.version; }).join(','));
var priceChange = audit[2].changes.filter(function (c) { return c.type === 'price' && c.productId === 'p1'; })[0];
check('la actividad registra el precio anterior y el nuevo',
  !!priceChange && priceChange.from === 5000 && priceChange.to === 5500, JSON.stringify(priceChange));
var stockChange = audit[0].changes.filter(function (c) { return c.type === 'stock' && c.productId === 'p4'; })[0];
check('la actividad registra el stock anterior y el nuevo',
  !!stockChange && stockChange.from === 1 && stockChange.to === 0, JSON.stringify(stockChange));
check('la actividad registra el producto retirado',
  audit[0].changes.some(function (c) { return c.type === 'removed' && c.productId === 'p2'; }));

var again = catalogo.sync();
check('sin cambios no se descarga nada', again.kind === 'current' && again.items === 0, JSON.stringify(again));

var before = JSON.stringify(catalogo.snapshot(1));
catalogo.snapshot(4);
check('consultar una versión no muta la memoria', JSON.stringify(catalogo.snapshot(1)) === before);

var reopened = Catalogo.create({ storage: storage, source: source });
check('el estado persiste entre aperturas', reopened.head() === 4 && reopened.snapshot().products.length === 6);
check('la actividad persiste entre aperturas', reopened.audit().length === 3);

// ---------------------------------------------------------------------------------------------
// Catálogo real del servidor (fixture capturado de https://tienda.tlalchichi.xyz)
// ---------------------------------------------------------------------------------------------
var fs = require('fs');
var realFull = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'bazar-full-v1.json'), 'utf8'));
// La foto completa no repite la vigencia: sale de /head, que es donde el servidor la declara.
var realHead = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'bazar-head-v1.json'), 'utf8'));
var remoteStorage = new MemoryStorage();
var remote = Catalogo.create({ storage: remoteStorage, source: source });

var installed = remote.installFrom(
  { version: realFull.version, stores: realFull.stores, products: realFull.products },
  { origin: 'remote', validUntil: realHead.validUntil }
);
check('la foto del servidor se instala con sus tiendas', installed.to === 1 && remote.snapshot().stores.length === 2,
  String(remote.snapshot().stores.length));
check('la foto del servidor trae sus 12 artículos', remote.snapshot().products.length === 12,
  String(remote.snapshot().products.length));
check('el catálogo queda marcado como remoto', remote.origin() === 'remote', String(remote.origin()));
check('se conserva la vigencia que declaró el servidor', remote.validUntil() === realHead.validUntil,
  String(remote.validUntil()));

var storeIds = remote.snapshot().stores.map(function (store) { return store.id; });
check('cada artículo apunta a una tienda conocida',
  remote.snapshot().products.every(function (item) { return storeIds.indexOf(item.storeId) >= 0; }),
  remote.snapshot().products.map(function (item) { return item.storeId; }).join(','));

var first = remote.snapshot().products[0];
var applied = remote.applyRemote({
  to: 2,
  serverTime: Date.now(),
  patches: [{
    version: 2, at: Date.now(), added: [], removed: [],
    updated: [{ id: first.id, priceCents: first.priceCents + 500, stock: Math.max(0, (first.stock || 0) - 1) }]
  }]
});
check('el delta del servidor sube la versión y reporta un cambio',
  applied.to === 2 && applied.updated === 1 && applied.kind === 'delta', JSON.stringify(applied));
var updated = remote.snapshot().products.filter(function (item) { return item.id === first.id; })[0];
check('el precio nuevo queda aplicado en memoria', updated.priceCents === first.priceCents + 500,
  String(updated.priceCents));
var remoteAudit = remote.audit()[0].changes.filter(function (change) {
  return change.type === 'price' && change.productId === first.id;
})[0];
check('la actividad del servidor muestra precio anterior → nuevo',
  !!remoteAudit && remoteAudit.from === first.priceCents && remoteAudit.to === first.priceCents + 500,
  JSON.stringify(remoteAudit));

var quiet = remote.applyRemote({ to: 2, patches: [] });
check('sin parches devuelve "current" y no inventa cambios',
  quiet.kind === 'current' && quiet.items === 0, JSON.stringify(quiet));

remote.markChecked(realHead.validUntil);
check('consultar sin cambios deja registrada la vigencia', remote.validUntil() === realHead.validUntil,
  String(remote.validUntil()));

console.log('');
if (failures) {
  console.log(failures + ' comprobación(es) fallaron');
  process.exit(1);
}
console.log('todas las comprobaciones pasaron');
