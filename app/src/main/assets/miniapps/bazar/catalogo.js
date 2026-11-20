/*
 * Catálogo versionado del Mercado oficial — API de sincronización por deltas
 * =============================================================================================
 * Problema que resuelve: el catálogo de las tiendas oficiales cambia a diario (altas, bajas y
 * cambios de precio). Descargar el catálogo completo cada vez gasta datos y borra la memoria de
 * lo que había antes. Este módulo guarda:
 *
 *   - La foto completa UNA vez (`base`), cuando se instala el catálogo.
 *   - Un parche por cada versión posterior, con SOLO lo agregado, lo retirado y los campos que
 *     cambiaron. Sincronizar es descargar esos parches, nunca la foto completa otra vez.
 *   - El historial completo, así que cualquier versión anterior se puede reconstruir y consultar
 *     (`snapshot(v)`), incluidos los productos que ya se retiraron.
 *
 * Contrato HTTP que implementa `source` (ver docs/MERCADO_API.md):
 *   GET /catalog?full=1          -> { version, stores, products }            (solo la 1a vez)
 *   GET /catalog/delta?since=N   -> { to, patches: [ { version, at, added, removed, updated } ] }
 *   GET /catalog/head            -> { version }
 *
 * Cada parche es un objeto:
 *   { version: 5, at: 1730000000000,
 *     added:   [ { id, storeId, title, priceCents, emoji, tag, createdAt } ],
 *     removed: [ "p-123" ],
 *     updated: [ { id: "p-9", priceCents: 9500 } ] }
 *
 * El módulo no toca la red: recibe un `source` y una `storage` (localStorage o un Map en tests),
 * de modo que se puede probar sin WebView y cambiar de backend sin tocar la UI.
 */
(function (root, factory) {
  var api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (root) root.MercadoCatalogo = api;
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this), function () {
  'use strict';

  function clone(value) {
    return JSON.parse(JSON.stringify(value));
  }

  /**
   * Aplica un parche a una foto del catálogo. `updated` sólo trae los campos que cambiaron; el
   * resto se conserva. Devuelve una foto nueva (no muta la anterior).
   */
  function applyDelta(snapshot, patch) {
    var byId = {};
    var order = [];
    (snapshot.products || []).forEach(function (product) {
      byId[product.id] = clone(product);
      order.push(product.id);
    });

    (patch.removed || []).forEach(function (id) {
      if (byId[id]) {
        delete byId[id];
        order = order.filter(function (key) { return key !== id; });
      }
    });

    (patch.updated || []).forEach(function (change) {
      var target = byId[change.id];
      if (!target) return;
      Object.keys(change).forEach(function (field) {
        if (field !== 'id') target[field] = change[field];
      });
    });

    (patch.added || []).forEach(function (product) {
      if (!byId[product.id]) order.push(product.id);
      byId[product.id] = clone(product);
    });

    return {
      version: patch.version,
      stores: clone(snapshot.stores || []),
      products: order.map(function (id) { return byId[id]; })
    };
  }

  function mergePatches(patches) {
    var added = [];
    var removed = [];
    var updated = [];
    patches.forEach(function (patch) {
      added = added.concat(clone(patch.added || []));
      removed = removed.concat(clone(patch.removed || []));
      updated = updated.concat(clone(patch.updated || []));
    });
    return { added: added, removed: removed, updated: updated };
  }

  function create(options) {
    var settings = options || {};
    var storage = settings.storage;
    var source = settings.source;
    var key = settings.key || 'nahua.mercado.catalogo.v1';

    function read() {
      try {
        var raw = storage.getItem(key);
        return raw ? JSON.parse(raw) : null;
      } catch (error) {
        return null;
      }
    }

    function write(state) {
      try {
        storage.setItem(key, JSON.stringify(state));
      } catch (error) {
        // Sin espacio: la sesión sigue con el catálogo en memoria.
      }
    }

    var state = read();
    if (state && (!state.base || !state.head)) state = null;

    function installed() {
      return !!state;
    }

    function head() {
      return state ? state.head : 0;
    }

    function total() {
      var current = snapshot();
      return current ? current.products.length : 0;
    }

    /** Reconstruye el catálogo tal como estaba en `version` (o el actual si se omite). */
    function snapshot(version) {
      if (!state) return null;
      var wanted = version == null ? state.head : Number(version);
      if (!isFinite(wanted) || wanted < state.base.version) wanted = state.base.version;
      if (wanted > state.head) wanted = state.head;

      var photo = clone(state.base);
      photo.version = state.base.version;
      state.patches.forEach(function (patch) {
        if (patch.version <= wanted) photo = applyDelta(photo, patch);
      });
      photo.version = wanted;
      return photo;
    }

    /** Instalación inicial con una foto ya obtenida (servidor o demo). */
    function installFrom(snapshot, options) {
      var settings = options || {};
      var products = (snapshot.products || []).length;
      state = {
        base: clone(snapshot),
        patches: [],
        head: snapshot.version,
        installedAt: Date.now(),
        syncedAt: Date.now(),
        checkedAt: Date.now(),
        validUntil: settings.validUntil || null,
        origin: settings.origin || 'remote',
        log: [{
          kind: 'full',
          version: snapshot.version,
          at: Date.now(),
          added: products,
          removed: 0,
          updated: 0,
          items: products
        }]
      };
      write(state);
      return {
        kind: 'full',
        from: 0,
        to: snapshot.version,
        added: products,
        removed: 0,
        updated: 0,
        items: products,
        completeCatalogue: true,
        origin: state.origin
      };
    }

    /** Instalación de respaldo: la foto que viene dentro del bundle (modo demostración). */
    function install() {
      return installFrom(source.base(), { origin: 'demo' });
    }

    /**
     * Aplica lo que devolvió el servidor (`{ to, patches: [...] }`) sin volver a pedir la foto.
     * Es la operación normal: sólo altas, bajas, precios y stock.
     */
    function applyRemote(payload, options) {
      if (!state) return null;
      var settings = options || {};
      var from = state.head;
      var list = (payload && payload.patches) ? payload.patches : [];
      var applied = 0;

      list.forEach(function (patch) {
        if (patch.version > state.head) {
          state.patches.push(clone(patch));
          state.head = patch.version;
          applied += 1;
        }
      });

      var merged = mergePatches(list.filter(function (patch) { return patch.version > from; }));
      state.checkedAt = Date.now();
      if (applied) state.syncedAt = state.checkedAt;
      if (settings.validUntil) state.validUntil = settings.validUntil;
      state.origin = state.origin || 'remote';
      write(state);

      return {
        kind: applied ? 'delta' : 'current',
        from: from,
        to: state.head,
        added: merged.added.length,
        removed: merged.removed.length,
        updated: merged.updated.length,
        items: merged.added.length + merged.removed.length + merged.updated.length,
        completeCatalogue: false,
        origin: state.origin
      };
    }

    /** El servidor dijo que no hay nada nuevo: queda registrada la consulta y su vigencia. */
    function markChecked(validUntil) {
      if (!state) return;
      state.checkedAt = Date.now();
      if (validUntil) state.validUntil = validUntil;
      write(state);
    }

    /** Descarga SOLO lo que cambió desde la versión en memoria (modo demostración). */
    function sync() {
      if (!state) return install();

      var from = state.head;
      var remoteHead = typeof source.head === 'function' ? source.head() : null;
      if (remoteHead != null && remoteHead === from) {
        state.checkedAt = Date.now();
        write(state);
        return {
          kind: 'current', from: from, to: from,
          added: 0, removed: 0, updated: 0, items: 0, completeCatalogue: false
        };
      }

      var payload = source.delta(from);
      if (!payload || !payload.patches || !payload.patches.length) {
        state.checkedAt = Date.now();
        write(state);
        return {
          kind: 'current', from: from, to: payload && payload.to != null ? payload.to : from,
          added: 0, removed: 0, updated: 0, items: 0, completeCatalogue: false
        };
      }

      payload.patches.forEach(function (patch) {
        if (patch.version > state.head) {
          state.patches.push(clone(patch));
          state.head = patch.version;
        }
      });

      var merged = mergePatches(payload.patches);
      state.log.push({
        kind: 'delta',
        version: state.head,
        at: Date.now(),
        added: merged.added.length,
        removed: merged.removed.length,
        updated: merged.updated.length,
        items: merged.added.length + merged.removed.length + merged.updated.length
      });
      state.syncedAt = Date.now();
      state.checkedAt = state.syncedAt;
      write(state);

      return {
        kind: 'delta',
        from: from,
        to: state.head,
        added: merged.added.length,
        removed: merged.removed.length,
        updated: merged.updated.length,
        items: merged.added.length + merged.removed.length + merged.updated.length,
        completeCatalogue: false
      };
    }

    /** Versiones conocidas, con lo que trajo cada una. La primera es la foto completa. */
    function history() {
      if (!state) return [];
      var entries = [{
        version: state.base.version,
        kind: 'full',
        added: (state.base.products || []).length,
        removed: 0,
        updated: 0,
        at: state.installedAt || (state.base.at || null)
      }];
      state.patches.forEach(function (patch) {
        entries.push({
          version: patch.version,
          kind: 'delta',
          added: (patch.added || []).length,
          removed: (patch.removed || []).length,
          updated: (patch.updated || []).length,
          at: patch.at || null
        });
      });
      return entries;
    }

    /** Lo que costaría sincronizar ahora mismo, sin aplicarlo. */
    function pending() {
      if (!state) return { from: 0, to: source.head ? source.head() : 0, items: 0, patches: 0 };
      var payload = source.delta(state.head);
      if (!payload || !payload.patches) return { from: state.head, to: state.head, items: 0, patches: 0 };
      var merged = mergePatches(payload.patches);
      return {
        from: state.head,
        to: payload.to != null ? payload.to : state.head,
        items: merged.added.length + merged.removed.length + merged.updated.length,
        patches: payload.patches.length
      };
    }

    /** Cuándo se aplicó el último cambio del catálogo. */
    function syncedAt() {
      return state ? (state.syncedAt || state.installedAt || null) : null;
    }

    /** De dónde salió el catálogo: 'remote' (servidor) o 'demo' (bundle). */
    function origin() {
      return state ? (state.origin || 'demo') : null;
    }

    /** Vigencia declarada por el servidor (epoch ms) o null. */
    function validUntil() {
      return state ? (state.validUntil || null) : null;
    }

    /** Cuándo se consultó al servidor por última vez (aunque no hubiera cambios). */
    function checkedAt() {
      return state ? (state.checkedAt || state.syncedAt || state.installedAt || null) : null;
    }

    /**
     * Actividad del catálogo: qué cambió en cada versión, con el valor anterior y el nuevo.
     * Se calcula comparando las fotos reconstruidas (no depende de que el servidor mande un
     * campo extra), así sirve también para auditar parches ya guardados.
     */
    function audit() {
      if (!state) return [];
      var entries = [];
      var previous = clone(state.base);
      previous.version = state.base.version;

      state.patches.forEach(function (patch) {
        var next = applyDelta(previous, patch);
        var before = {};
        var after = {};
        (previous.products || []).forEach(function (product) { before[product.id] = product; });
        (next.products || []).forEach(function (product) { after[product.id] = product; });

        var changes = [];
        Object.keys(after).forEach(function (id) {
          var now = after[id];
          var then = before[id];
          if (!then) {
            changes.push({ type: 'added', productId: id, title: now.title, to: now.priceCents });
            return;
          }
          if (now.priceCents !== then.priceCents) {
            changes.push({ type: 'price', productId: id, title: now.title, from: then.priceCents, to: now.priceCents });
          }
          if (now.stock !== then.stock) {
            changes.push({ type: 'stock', productId: id, title: now.title, from: then.stock, to: now.stock });
          }
          if (now.title !== then.title) {
            changes.push({ type: 'title', productId: id, title: now.title, from: then.title, to: now.title });
          }
        });
        Object.keys(before).forEach(function (id) {
          if (!after[id]) {
            changes.push({ type: 'removed', productId: id, title: before[id].title, from: before[id].priceCents });
          }
        });

        entries.push({ version: patch.version, at: patch.at || null, changes: changes });
        previous = next;
      });

      return entries.reverse();
    }

    function reset() {
      try { storage.removeItem(key); } catch (error) { /* sin storage */ }
      state = null;
    }

    return {
      installed: installed,
      head: head,
      total: total,
      snapshot: snapshot,
      install: install,
      installFrom: installFrom,
      applyRemote: applyRemote,
      markChecked: markChecked,
      sync: sync,
      history: history,
      pending: pending,
      syncedAt: syncedAt,
      checkedAt: checkedAt,
      origin: origin,
      validUntil: validUntil,
      audit: audit,
      reset: reset
    };
  }

  return { create: create, applyDelta: applyDelta, mergePatches: mergePatches };
});
