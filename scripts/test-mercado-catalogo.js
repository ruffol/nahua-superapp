/*
 * Prueba del catálogo versionado del Mercado (sin WebView, con node).
 *   node scripts/test-mercado-catalogo.js
 *
 * Valida lo que el requisito pide y lo que un refactor rompe en silencio:
 *   1. La primera instalación es la única descarga completa.
 *   2. Sincronizar trae sólo altas/bajas/cambios, nunca el catálogo entero.
 *   3. El catálogo viejo queda consultable, incluidos los productos retirados.
 *   4. `applyDelta` no muta la foto anterior (una vista histórica no puede contaminarse).
 */
'use strict';

var path = require('path');
var Catalogo = require(path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'miniapps', 'mercado', 'catalogo.js'));

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

function product(id, title, priceCents, createdAt) {
  return { id: id, storeId: 's1', title: title, priceCents: priceCents, emoji: '🧺', tag: 'nuevo', createdAt: createdAt };
}

var base = {
  version: 1,
  stores: [{ id: 's1', name: 'Tienda Uno', accent: '#2F6B4F', pinned: ['p1'] }],
  products: [
    product('p1', 'Taza', 5000, 1),
    product('p2', 'Blusa', 8000, 2),
    product('p3', 'Conserva', 3000, 3),
    product('p4', 'Guitarra', 12000, 4),
    product('p5', 'Maceta', 2000, 5)
  ]
};

var patches = [
  { version: 2, at: 100, added: [product('p6', 'Rebozo', 9000, 6)], removed: ['p3'], updated: [{ id: 'p1', priceCents: 5500 }] },
  { version: 3, at: 200, added: [product('p7', 'Café', 4000, 7)], removed: [], updated: [] },
  { version: 4, at: 300, added: [product('p8', 'Semillas', 2500, 8)], removed: ['p2'], updated: [] }
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

console.log('catálogo versionado');

var install = catalogo.sync();
check('la primera sincronización es la descarga completa', install.kind === 'full' && install.completeCatalogue === true);
check('instala la foto base (5 fichas)', install.added === 5 && install.to === 1, JSON.stringify(install));
check('el head local queda en v1', catalogo.head() === 1);
check('snapshot() actual trae 5 productos', catalogo.snapshot().products.length === 5);

var delta = catalogo.sync();
check('la segunda sincronización es un delta', delta.kind === 'delta' && delta.completeCatalogue === false, JSON.stringify(delta));
check('el delta abarca v1 → v4', delta.from === 1 && delta.to === 4);
check('sólo descarga 3 altas', delta.added === 3, String(delta.added));
check('sólo descarga 2 bajas', delta.removed === 2, String(delta.removed));
check('sólo descarga 1 cambio', delta.updated === 1, String(delta.updated));
check('fueron 6 fichas, no las 8 del catálogo completo', delta.items === 6, String(delta.items));

var current = catalogo.snapshot();
var currentIds = current.products.map(function (p) { return p.id; }).sort();
check('el catálogo actual tiene 6 productos', current.products.length === 6, String(current.products.length));
check('p3 y p2 quedaron retirados', currentIds.indexOf('p3') < 0 && currentIds.indexOf('p2') < 0, currentIds.join(','));
check('p6, p7 y p8 están agregados', currentIds.indexOf('p6') >= 0 && currentIds.indexOf('p7') >= 0 && currentIds.indexOf('p8') >= 0);
check('el cambio de precio de p1 se aplicó', current.products.filter(function (p) { return p.id === 'p1'; })[0].priceCents === 5500);

var old = catalogo.snapshot(1);
check('la versión 1 se reconstruye con 5 productos', old.products.length === 5, String(old.products.length));
check('la versión 1 conserva el producto retirado p3', old.products.some(function (p) { return p.id === 'p3'; }));
check('la versión 1 conserva el precio viejo de p1', old.products.filter(function (p) { return p.id === 'p1'; })[0].priceCents === 5000);

var v2 = catalogo.snapshot(2);
check('la versión 2 refleja su propio parche (5 fichas)', v2.products.length === 5, String(v2.products.length));
check('la versión 2 ya no tiene p3', !v2.products.some(function (p) { return p.id === 'p3'; }));
check('la versión 2 todavía no tiene p7', !v2.products.some(function (p) { return p.id === 'p7'; }));

var history = catalogo.history();
check('el historial lista las 4 versiones', history.length === 4, JSON.stringify(history.map(function (h) { return h.version; })));
check('la primera entrada es la foto completa', history[0].kind === 'full' && history[0].version === 1);

var again = catalogo.sync();
check('sin cambios no se descarga nada', again.kind === 'current' && again.items === 0, JSON.stringify(again));

// Una vista histórica no puede contaminar la foto que ya se mostró.
var before = JSON.stringify(catalogo.snapshot(1));
catalogo.snapshot(4);
check('consultar una versión no muta la memoria', JSON.stringify(catalogo.snapshot(1)) === before);

// Reabrir con la misma storage debe conservar head y parches (memoria entre sesiones).
var reopened = Catalogo.create({ storage: storage, source: source });
check('el estado persiste entre aperturas', reopened.head() === 4 && reopened.snapshot().products.length === 6);

console.log('');
if (failures) {
  console.log(failures + ' comprobación(es) fallaron');
  process.exit(1);
}
console.log('todas las comprobaciones pasaron');
