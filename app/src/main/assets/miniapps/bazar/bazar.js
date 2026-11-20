/*
 * Bazar/Trueque + Mercado oficial
 * ---------------------------------------------------------------------------------------------
 * Un solo lugar para la comunidad estudiantil:
 *
 *  - Arriba, los banners de las ORGANIZACIONES OFICIALES (tiendas que siempre tienen stock y
 *    merecen un lugar permanente). Tocar un banner entra a la tienda, con sus productos fijados
 *    primero y los últimos agregados después, stock a la vista y compra con la cartera.
 *  - Debajo, el bazar de la comunidad: publicas lo que ya no usas, pones TÚ el precio y decides si
 *    aceptas trueque.
 *
 * Trueque: marcar «Me interesa» es privado. Sólo cuando el interés es mutuo se abre el chat y se
 * comparte el contacto (nombre + teléfono). Nada de valoraciones automáticas: el único valor es el
 * que pone quien publica.
 *
 * Catálogo oficial: versionado. La foto completa se descarga una vez y después sólo viajan altas,
 * bajas, precios y stock. El bazar muestra si el catálogo está actualizado o no, y guarda la
 * actividad (qué cambió, cuándo y de cuánto a cuánto) para poder auditar fraudes o engaños.
 *
 * Reportes: cualquier artículo o usuario se puede reportar por artículos prohibidos, precios
 * engañosos, falta de stock, actitudes irrespetuosas, spam u otro motivo.
 */
(function () {
  'use strict';

  var DAY = 24 * 60 * 60 * 1000;
  var NOW = Date.now();

  var STORE_KEY = 'nahua.bazar.v4';
  var LEGACY_KEYS = ['nahua.bazar.v3', 'nahua.bazar.v2', 'nahua.bazar.v1'];
  var PURCHASES_KEY = 'nahua.bazar.compras.v1';
  var CATALOG_KEY = 'nahua.bazar.catalogo.v1';
  var STALE_MS = DAY;
  var SERVER_BASE = 'https://tienda.tlalchichi.xyz';

  var CATEGORIES = [
    { id: 'PHONES', label: 'Celulares', emoji: '📱' },
    { id: 'COMPUTERS', label: 'Computadoras', emoji: '💻' },
    { id: 'ELECTRONICS', label: 'Electrónica', emoji: '🎧' },
    { id: 'APPLIANCES', label: 'Línea blanca', emoji: '🧺' },
    { id: 'FURNITURE', label: 'Muebles', emoji: '🪑' },
    { id: 'TOOLS', label: 'Herramientas', emoji: '🔧' },
    { id: 'BIKES', label: 'Bicis', emoji: '🚲' },
    { id: 'SPORTS', label: 'Deportes', emoji: '⚽' },
    { id: 'CLOTHING', label: 'Ropa', emoji: '👕' },
    { id: 'BOOKS', label: 'Libros', emoji: '📚' },
    { id: 'TOYS', label: 'Juguetes', emoji: '🧸' },
    { id: 'TEXTILES', label: 'Textiles y bordados', emoji: '🧶' },
    { id: 'CRAFTS', label: 'Artesanías', emoji: '🏺' },
    { id: 'FOOD', label: 'Comida y conservas', emoji: '🫙' },
    { id: 'PLANTS', label: 'Plantas y semillas', emoji: '🌱' },
    { id: 'ART', label: 'Arte y pintura', emoji: '🎨' },
    { id: 'SCHOOL', label: 'Escolar', emoji: '📓' },
    { id: 'MUSIC', label: 'Instrumentos', emoji: '🎸' },
    { id: 'COLLECTIBLES', label: 'Coleccionables', emoji: '🪙' },
    { id: 'OTHER', label: 'Otro', emoji: '📦' }
  ];

  var CONDITIONS = [
    { id: 'NEW', label: 'Nuevo, sin abrir', short: 'Nuevo' },
    { id: 'LIKE_NEW', label: 'Como nuevo', short: 'Como nuevo' },
    { id: 'GOOD', label: 'Buen estado', short: 'Buen estado' },
    { id: 'WORN', label: 'Usado, con marcas', short: 'Usado' },
    { id: 'FOR_PARTS', label: 'Para refacción', short: 'Refacción' }
  ];

  var EMOJIS = ['📦', '🧶', '🏺', '🫙', '🌱', '🎨', '📓', '🚲', '📱', '💻', '🔧', '🪑', '🎸', '🎧', '☕', '🧺', '🪙', '🎒', '🍳', '🚴'];

  var MEETING_POINTS = [
    'Explanada de la facultad',
    'Cafetería del campus',
    'Biblioteca, planta baja',
    'Jardín principal del centro',
    'Portal del mercado'
  ];

  var REPORT_REASONS = [
    { id: 'prohibido', label: 'Artículo prohibido' },
    { id: 'precio', label: 'Precio engañoso o distinto al acordado' },
    { id: 'stock', label: 'No tiene el artículo o no hay stock' },
    { id: 'actitud', label: 'Actitud irrespetuosa o amenazas' },
    { id: 'spam', label: 'Spam o publicación repetida' },
    { id: 'otro', label: 'Otro motivo' }
  ];

  var AVATAR_COLORS = ['#7E2C44', '#4F7A52', '#3B5BA5', '#B4452F', '#7A4E12', '#4E7C86'];

  // ---------------------------------------------------------------------------------------------
  // Catálogo oficial: organizaciones con stock permanente
  // ---------------------------------------------------------------------------------------------

  var STORES = [
    {
      id: 'coop',
      name: 'Cooperativa del Campus',
      short: 'Cooperativa',
      tagline: 'Útiles, tazas y termos para la semana',
      accent: '#2F6B4F',
      deep: '#1E4A36',
      emoji: '🏫',
      offer: '10 % en útiles al mostrar tu credencial',
      since: '2019',
      official: true,
      pinned: ['c1']
    },
    {
      id: 'telar',
      name: 'Telar de Suchitlán',
      short: 'Telar',
      tagline: 'Textiles y bordados de nuestras comunidades',
      accent: '#7E2C44',
      deep: '#5A1D30',
      emoji: '🧶',
      offer: 'Rebozos con envío gratis al campus',
      since: '2021',
      official: true,
      pinned: ['t1']
    },
    {
      id: 'cafe',
      name: 'Café de Altura',
      short: 'Café',
      tagline: 'Café de Comala y conservas de temporada',
      accent: '#8A5A22',
      deep: '#5F3C12',
      emoji: '☕',
      offer: '2 × 1 en café molido los viernes',
      since: '2022',
      official: true,
      pinned: ['k1']
    },
    {
      id: 'taller',
      name: 'Taller Barro y Palma',
      short: 'Taller',
      tagline: 'Barro, palma y piezas hechas a mano',
      accent: '#3B5BA5',
      deep: '#233A6E',
      emoji: '🏺',
      offer: 'Juego de tazas con 15 % de descuento',
      since: '2020',
      official: true,
      pinned: ['a1']
    }
  ];

  function official(id, storeId, title, priceCents, stock, emoji, tag, daysAgo) {
    return {
      id: id,
      storeId: storeId,
      title: title,
      priceCents: priceCents,
      stock: stock,
      emoji: emoji,
      tag: tag,
      createdAt: NOW - daysAgo * DAY
    };
  }

  // Foto completa: la única descarga entera.
  var BASE = {
    version: 1,
    stores: STORES,
    products: [
      official('c1', 'coop', 'Taza de cerámica del campus', 6500, 8, '☕', 'Fijado', 30),
      official('c2', 'coop', 'Termo de acero 1 L', 24000, 5, '🧴', 'Más vendido', 29),
      official('c3', 'coop', 'Cuaderno cosido 100 hojas', 4500, 20, '📓', 'Nuevo', 28),
      official('c4', 'coop', 'Mochila ligera para laptop', 38000, 4, '🎒', '', 27),
      official('c5', 'coop', 'Calculadora científica', 22000, 6, '🖩', '', 26),
      official('c6', 'coop', 'Gorra bordada del campus', 15000, 3, '🧢', '', 25),
      official('t1', 'telar', 'Rebozo de telar de cintura', 95000, 2, '🧶', 'Fijado', 24),
      official('t2', 'telar', 'Blusa bordada talla M', 62000, 5, '👚', 'Hecho a mano', 23),
      official('t3', 'telar', 'Morral de hilo tejido', 28000, 7, '👝', '', 22),
      official('t4', 'telar', 'Juego de servilletas bordadas', 21000, 9, '🧵', '', 21),
      official('t5', 'telar', 'Chal de lana de borrego', 54000, 3, '🧣', '', 20),
      official('k1', 'cafe', 'Café en grano de Comala 1 kg', 12000, 15, '☕', 'Fijado', 19),
      official('k2', 'cafe', 'Conserva de tamarindo 500 g', 8500, 10, '🫙', 'De temporada', 18),
      official('k3', 'cafe', 'Miel de abeja 1 L', 16000, 6, '🍯', '', 17),
      official('k4', 'cafe', 'Pan de elote (charola)', 7000, 0, '🌽', 'Horneado hoy', 16),
      official('k5', 'cafe', 'Chocolate de metate 400 g', 11000, 8, '🍫', '', 15),
      official('a1', 'taller', 'Olla de barro grande', 34000, 4, '🏺', 'Fijado', 14),
      official('a2', 'taller', 'Juego de 4 tazas de barro', 26000, 6, '🍵', 'Hecho a mano', 13),
      official('a3', 'taller', 'Canasta de palma tejida', 18000, 5, '🧺', '', 12),
      official('a4', 'taller', 'Molcajete de piedra volcánica', 29000, 3, '🥑', '', 11),
      official('a5', 'taller', 'Figuras de barro pintadas', 9500, 12, '🎨', '', 10),
      official('a6', 'taller', 'Plato decorado con flores', 14000, 7, '🍽️', '', 9)
    ]
  };

  // Parches: sólo lo que cambió. `updated` lleva los campos nuevos y el cliente conserva el resto.
  var PATCHES = [
    {
      version: 2,
      at: NOW - 6 * DAY,
      added: [
        official('c7', 'coop', 'Sudadera del campus', 42000, 10, '🧥', 'Nuevo', 8),
        official('k6', 'cafe', 'Café molido 500 g', 9000, 14, '☕', 'Nuevo', 7)
      ],
      removed: ['c5'],
      updated: [{ id: 'k1', priceCents: 13000, stock: 12 }]
    },
    {
      version: 3,
      at: NOW - 3 * DAY,
      added: [
        official('t6', 'telar', 'Cinturón tejido en telar', 19000, 8, '🪢', 'Nuevo', 5),
        official('a7', 'taller', 'Jarra de barro esmaltada', 24000, 5, '🫖', 'Nuevo', 4)
      ],
      removed: ['k4'],
      updated: [{ id: 't2', stock: 2 }]
    },
    {
      version: 4,
      at: NOW - 1 * DAY,
      added: [
        official('c8', 'coop', 'Agenda 2027', 12000, 25, '🗓️', 'Nuevo', 2),
        official('a8', 'taller', 'Maceta de barro para suculenta', 8000, 18, '🪴', 'Nuevo', 1)
      ],
      removed: ['c6'],
      updated: [{ id: 't2', priceCents: 58000 }, { id: 'a1', stock: 1 }, { id: 'c4', stock: 0 }]
    }
  ];

  /**
   * Adaptador del "servidor". En producción se cambia por fetch a los endpoints de
   * docs/MERCADO_API.md sin tocar la UI ni el almacenamiento.
   */
  var SOURCE = {
    base: function () { return JSON.parse(JSON.stringify(BASE)); },
    head: function () { return PATCHES.length ? PATCHES[PATCHES.length - 1].version : BASE.version; },
    delta: function (since) {
      var from = Number(since) || 0;
      var list = PATCHES.filter(function (patch) { return patch.version > from; });
      var to = list.length ? list[list.length - 1].version : from;
      return { to: to, patches: JSON.parse(JSON.stringify(list)) };
    }
  };

  var catalogo = window.MercadoCatalogo.create({
    storage: window.localStorage,
    source: SOURCE,
    key: CATALOG_KEY
  });

  if (!catalogo.installed()) {
    // Respaldo offline: la foto del bundle. Si el servidor responde, se reemplaza al arrancar.
    catalogo.install();
  }

  /** El puente nativo hace el HTTP: la WebView tiene bloqueado todo tráfico externo. */
  function serverAvailable() {
    return !!(window.NahuaNative && window.Nahua && typeof Nahua.catalogHead === 'function');
  }

  function loadPurchases() {
    try {
      var raw = window.localStorage.getItem(PURCHASES_KEY);
      return raw ? JSON.parse(raw) : {};
    } catch (error) {
      return {};
    }
  }

  var purchases = loadPurchases();

  function savePurchases() {
    try { window.localStorage.setItem(PURCHASES_KEY, JSON.stringify(purchases)); } catch (error) { /* sin storage */ }
  }

  // ---------------------------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------------------------

  function categoryById(id) {
    for (var i = 0; i < CATEGORIES.length; i++) if (CATEGORIES[i].id === id) return CATEGORIES[i];
    return CATEGORIES[CATEGORIES.length - 1];
  }

  function conditionById(id) {
    for (var i = 0; i < CONDITIONS.length; i++) if (CONDITIONS[i].id === id) return CONDITIONS[i];
    return CONDITIONS[2];
  }

  function esc(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function money(cents) {
    var value = Math.round(Number(cents) || 0);
    var negative = value < 0;
    var abs = Math.abs(value);
    var units = Math.floor(abs / 100);
    var decimals = abs % 100;
    var text = units.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    if (decimals > 0) text += '.' + (decimals < 10 ? '0' + decimals : String(decimals));
    return (negative ? '-$' : '$') + text;
  }

  function pesosToCents(value) {
    var number = Number(String(value).replace(/[^0-9.]/g, ''));
    if (!isFinite(number) || number <= 0) return 0;
    return Math.round(number * 100);
  }

  function centsToPesos(cents) {
    return Math.round((Number(cents) || 0) / 100);
  }

  function initials(name) {
    var parts = String(name || '?').trim().split(/\s+/);
    var first = parts[0] ? parts[0].charAt(0) : '?';
    var second = parts.length > 1 ? parts[parts.length - 1].charAt(0) : '';
    return (first + second).toUpperCase();
  }

  function avatarColor(id) {
    var hash = 0;
    var key = String(id || '');
    for (var i = 0; i < key.length; i++) hash = (hash * 31 + key.charCodeAt(i)) % 997;
    return AVATAR_COLORS[hash % AVATAR_COLORS.length];
  }

  function ago(timestamp) {
    if (!timestamp) return 'sin registro';
    var diff = Date.now() - (Number(timestamp) || 0);
    var minute = 60000;
    if (diff < minute) return 'ahora';
    if (diff < 60 * minute) return 'hace ' + Math.round(diff / minute) + ' min';
    if (diff < 24 * 60 * minute) return 'hace ' + Math.round(diff / (60 * minute)) + ' h';
    var days = Math.round(diff / (24 * 60 * minute));
    if (days === 1) return 'ayer';
    if (days < 30) return 'hace ' + days + ' d';
    return 'hace ' + Math.round(days / 30) + ' meses';
  }

  /** México: 10 dígitos. Se aceptan +52 / 52 al frente y se descartan espacios y guiones. */
  function phoneDigits(raw) {
    var digits = String(raw == null ? '' : raw).replace(/\D/g, '');
    if (digits.length === 12 && digits.indexOf('52') === 0) digits = digits.slice(2);
    if (digits.length === 13 && digits.indexOf('521') === 0) digits = digits.slice(3);
    return digits;
  }

  function formatPhone(raw) {
    var digits = phoneDigits(raw);
    if (digits.length !== 10) return String(raw || '');
    return digits.slice(0, 3) + ' ' + digits.slice(3, 6) + ' ' + digits.slice(6);
  }

  // ---------------------------------------------------------------------------------------------
  // Catálogo: estado, stock y actividad
  // ---------------------------------------------------------------------------------------------

  /** Tiendas reales del catálogo; si aún no hay catálogo del servidor, las del bundle. */
  function storeList() {
    var photo = catalogo.snapshot();
    var list = photo && photo.stores ? photo.stores : null;
    return list && list.length ? list : STORES;
  }

  function storeById(id) {
    var list = storeList();
    for (var i = 0; i < list.length; i++) if (list[i].id === id) return list[i];
    return list[0];
  }

  function catalogSnapshot(version) {
    return catalogo.snapshot(version);
  }

  function catalogProducts(version) {
    var photo = catalogSnapshot(version);
    return photo ? photo.products : [];
  }

  function officialProduct(id) {
    var list = catalogProducts();
    for (var i = 0; i < list.length; i++) if (list[i].id === id) return list[i];
    return null;
  }

  function productsForStore(storeId, version) {
    var store = storeById(storeId);
    var list = catalogProducts(version).filter(function (product) { return product.storeId === storeId; });
    return list.sort(function (a, b) {
      var pinnedA = store.pinned.indexOf(a.id);
      var pinnedB = store.pinned.indexOf(b.id);
      if (pinnedA >= 0 || pinnedB >= 0) {
        if (pinnedA < 0) return 1;
        if (pinnedB < 0) return -1;
        return pinnedA - pinnedB;
      }
      return (b.createdAt || 0) - (a.createdAt || 0);
    });
  }

  function latestOfficial(limit) {
    return catalogProducts().slice().sort(function (a, b) {
      return (b.createdAt || 0) - (a.createdAt || 0);
    }).slice(0, limit);
  }

  /** Stock del servidor menos lo que ya compraste en este dispositivo. */
  function localStock(product) {
    var bought = Number(purchases[product.id] || 0);
    return Math.max(0, Number(product.stock || 0) - bought);
  }

  function catalogStatus() {
    var origin = catalogo.origin() || 'demo';
    var head = catalogo.head();
    var syncedAt = catalogo.syncedAt() || Date.now();
    var validUntil = catalogo.validUntil();
    var ageMs = Date.now() - syncedAt;
    var remote = origin === 'remote' ? (ui.remoteHead || head) : head;
    var pending = origin === 'remote' && remote > head;
    var expired = validUntil ? Date.now() > validUntil : false;
    var stale = origin !== 'remote' || pending || expired || ageMs > STALE_MS;

    var label;
    if (origin !== 'remote') {
      label = 'Sin servidor · catálogo de demostración';
    } else if (ui.server === 'offline') {
      label = 'Sin conexión · catálogo guardado ' + ago(syncedAt);
    } else if (pending) {
      label = 'Hay cambios sin aplicar (v' + head + ' → v' + remote + ')';
    } else if (expired || ageMs > STALE_MS) {
      label = 'Catálogo sin actualizar desde ' + ago(syncedAt);
    } else {
      label = 'Catálogo al día · ' + ago(syncedAt);
    }

    return {
      head: head,
      remote: remote,
      pending: pending,
      syncedAt: syncedAt,
      ageMs: ageMs,
      stale: stale,
      origin: origin,
      validUntil: validUntil,
      label: label
    };
  }

  /**
   * Trae el catálogo del servidor por el puente nativo. Primera vez: foto completa. Después:
   * sólo el delta. Si ya se tenía el catálogo de demostración, lo reemplaza por el real.
   */
  function pullCatalog() {
    if (!serverAvailable()) {
      ui.server = 'offline';
      return Promise.resolve(null);
    }
    ui.server = 'loading';

    return Nahua.catalogHead().then(function (remoteHead) {
      ui.remoteHead = Number(remoteHead.version) || 0;

      if (catalogo.origin() !== 'remote') {
        return Nahua.catalogFull().then(function (full) {
          var result = catalogo.installFrom(
            { version: full.version, stores: full.stores, products: full.products },
            { origin: 'remote', validUntil: full.validUntil || remoteHead.validUntil }
          );
          ui.server = 'online';
          return result;
        });
      }

      if (ui.remoteHead > catalogo.head()) {
        return Nahua.catalogDelta(catalogo.head()).then(function (delta) {
          var result = catalogo.applyRemote(delta, { validUntil: remoteHead.validUntil });
          ui.server = 'online';
          return result;
        });
      }

      catalogo.markChecked(remoteHead.validUntil);
      ui.server = 'online';
      return {
        kind: 'current', from: catalogo.head(), to: ui.remoteHead,
        added: 0, removed: 0, updated: 0, items: 0, completeCatalogue: false, origin: 'remote'
      };
    }).catch(function (error) {
      ui.server = 'offline';
      return null;
    });
  }

  function freshnessChip(extraClass) {
    var status = catalogStatus();
    return '<button class="freshness ' + (status.stale ? 'stale' : 'ok') + ' ' + (extraClass || '') +
      '" data-act="catalogo">' +
      '<span class="dot-fresh"></span>' +
      '<span class="fresh-text">' + esc(status.label) + '</span>' +
      '<span class="fresh-cta">' + (status.pending ? 'Actualizar' : (status.origin !== 'remote' ? 'Conectar' : 'Ver catálogo')) + ' →</span>' +
    '</button>';
  }

  function auditLabel(change) {
    if (change.type === 'added') return 'Nuevo producto · ' + money(change.to);
    if (change.type === 'removed') return 'Retirado (estaba a ' + money(change.from) + ')';
    if (change.type === 'price') return 'Precio ' + money(change.from) + ' → ' + money(change.to);
    if (change.type === 'stock') {
      if (change.to === 0) return 'Sin stock (antes ' + change.from + ')';
      return 'Stock ' + change.from + ' → ' + change.to;
    }
    if (change.type === 'title') return 'Nombre: “' + change.from + '” → “' + change.to + '”';
    return change.type;
  }

  // ---------------------------------------------------------------------------------------------
  // Comunidad: datos
  // ---------------------------------------------------------------------------------------------

  function seedListing(list, owner, title, emoji, category, condition, askPrice, barter, desc) {
    return {
      id: list.id,
      ownerId: owner,
      title: title,
      description: desc || '',
      emoji: emoji,
      category: category,
      condition: condition,
      askingCents: Math.round(askPrice * 100),
      barter: !!barter,
      status: 'DISPONIBLE',
      createdAt: NOW - (list.daysAgo || 1) * DAY
    };
  }

  function seed() {
    var minute = 60000;

    var users = {
      me: { id: 'me', name: 'Ana Nahua', phone: '3121234567', town: 'Colima', program: 'Ing. en Sistemas', semester: '5º semestre', rating: 4.9, trades: 7, since: '2023' },
      beto: { id: 'beto', name: 'Beto Nieves', phone: '3121458790', town: 'Villa de Álvarez', program: 'Ing. Civil', semester: '7º semestre', rating: 4.6, trades: 5, since: '2022' },
      mari: { id: 'mari', name: 'Mari Preciado', phone: '3121789034', town: 'Comala', program: 'Nutrición', semester: '3º semestre', rating: 5.0, trades: 11, since: '2024' },
      chuy: { id: 'chuy', name: 'Chuy Cárdenas', phone: '3121904567', town: 'Suchitlán', program: 'Diseño Industrial', semester: '9º semestre', rating: 4.8, trades: 8, since: '2021' },
      tere: { id: 'tere', name: 'Tere Lomelí', phone: '3121567234', town: 'Manzanillo', program: 'Administración', semester: '5º semestre', rating: 4.5, trades: 3, since: '2023' },
      fer: { id: 'fer', name: 'Fer Alejandrez', phone: '3121345678', town: 'Colima', program: 'Música', semester: '6º semestre', rating: 4.7, trades: 9, since: '2022' },
      jorge: { id: 'jorge', name: 'Jorge Rincón', phone: '3121678901', town: 'Ixtlahuacán', program: 'Agronomía', semester: '8º semestre', rating: 4.4, trades: 4, since: '2023' },
      lucia: { id: 'lucia', name: 'Lucía Candelario', phone: '3121122334', town: 'Cuauhtémoc', program: 'Enfermería', semester: '2º semestre', rating: 4.9, trades: 2, since: '2025' }
    };

    var listings = [
      seedListing({ id: 'M1', daysAgo: 2 }, 'me', 'Mochila de senderismo 40 L', '🎒', 'SPORTS', 'LIKE_NEW', 1100, true,
        'La compré para una práctica de campo y la usé dos veces. Impermeable, con cubre mochila.'),
      seedListing({ id: 'M2', daysAgo: 3 }, 'me', 'Cafetera de prensa francesa', '☕', 'APPLIANCES', 'GOOD', 450, true,
        'De vidrio grueso, 600 ml. Limpia y sin fisuras.'),
      seedListing({ id: 'M3', daysAgo: 4 }, 'me', 'Bocina Bluetooth pequeña', '🔊', 'ELECTRONICS', 'GOOD', 400, true,
        'Compacta, ideal para el cuarto. Incluye cable de carga.'),
      seedListing({ id: 'M4', daysAgo: 6 }, 'me', 'Juego de llaves mecánicas', '🔧', 'TOOLS', 'GOOD', 700, false,
        'Doce piezas del 8 al 19 mm, en su estuche. Sin uso desde que me mudé.'),
      seedListing({ id: 'M5', daysAgo: 8 }, 'me', 'Apuntes y guías de Cálculo II', '📓', 'SCHOOL', 'GOOD', 180, false,
        'Carpeta con ejercicios resueltos y exámenes de tres semestres.'),

      seedListing({ id: 'B1', daysAgo: 1 }, 'beto', 'Bicicleta rodada 26', '🚲', 'BIKES', 'GOOD', 2900, true,
        'Bici de montaña con 21 velocidades. Le cambié los frenos hace un mes; detalles de pintura en el cuadro.'),
      seedListing({ id: 'B2', daysAgo: 5 }, 'beto', 'Patineta de trucos', '🛹', 'SPORTS', 'WORN', 550, false,
        'Ruedas nuevas, tabla raspada por el uso. Sirve para moverse al campus.'),

      seedListing({ id: 'M6', daysAgo: 1 }, 'mari', 'Blusa bordada de Suchitlán', '🧶', 'TEXTILES', 'LIKE_NEW', 850, true,
        'Bordada a mano por mi tía en Suchitlán. Talla M, la usé una vez para una presentación.'),
      seedListing({ id: 'M7', daysAgo: 2 }, 'mari', 'Conserva de tamarindo (6 frascos)', '🫙', 'FOOD', 'NEW', 500, true,
        'Hecha en casa en Comala este mes. Sin conservadores.'),

      seedListing({ id: 'C1', daysAgo: 1 }, 'chuy', 'Juego de ollas de barro de Suchitlán', '🏺', 'CRAFTS', 'NEW', 900, true,
        'Cinco piezas hechas en el taller de la familia. Nuevas, listas para cocinar.'),
      seedListing({ id: 'C2', daysAgo: 4 }, 'chuy', 'Rebozo de telar de cintura', '🧶', 'TEXTILES', 'GOOD', 1400, false,
        'Tejido en telar de cintura, tintes naturales. Tiene un hilo jalado en la orilla.'),

      seedListing({ id: 'T1', daysAgo: 3 }, 'tere', 'Laptop Lenovo ThinkPad', '💻', 'COMPUTERS', 'GOOD', 6500, false,
        'i5, 8 GB de RAM, SSD de 256 GB. Batería al 70 %, cargador original incluido.'),
      seedListing({ id: 'T2', daysAgo: 6 }, 'tere', 'Maleta de viaje 24"', '🧳', 'OTHER', 'GOOD', 800, true,
        'Con ruedas y candado. La cambié por una más chica para viajar a Manzanillo.'),

      seedListing({ id: 'F1', daysAgo: 2 }, 'fer', 'Guitarra acústica Yamaha', '🎸', 'MUSIC', 'GOOD', 1400, true,
        'Cuerdas nuevas, estuche de tela. Tiene marcas de uso en la caja, nada que afecte el sonido.'),
      seedListing({ id: 'F2', daysAgo: 5 }, 'fer', 'Cuadro de acuarela del Volcán', '🎨', 'ART', 'NEW', 650, false,
        'Pintado por mí, 30 × 40 cm con marco de madera. Firmado al reverso.'),

      seedListing({ id: 'J1', daysAgo: 2 }, 'jorge', 'Semillas de maíz criollo (1 kg)', '🌱', 'PLANTS', 'NEW', 380, true,
        'De la milpa de mi abuelo en Ixtlahuacán. Para siembra en maceta o huerto escolar.'),
      seedListing({ id: 'J2', daysAgo: 6 }, 'jorge', 'Caja de herramientas 40 piezas', '🧰', 'TOOLS', 'WORN', 500, true,
        'Dados, desarmadores y pinzas. La caja tiene marcas de taller, todo funciona.'),

      seedListing({ id: 'L1', daysAgo: 2 }, 'lucia', 'Calculadora científica CASIO', '🖩', 'SCHOOL', 'LIKE_NEW', 500, true,
        'Con funda y manual. La compré para Estadística y ya uso la del celular.'),
      seedListing({ id: 'L2', daysAgo: 4 }, 'lucia', 'Café de Comala en grano (1 kg)', '☕', 'FOOD', 'NEW', 520, true,
        'Tostado medio, molido a pedido. Lo traje del rancho de mi familia.'),
      seedListing({ id: 'L3', daysAgo: 7 }, 'lucia', 'Plantas de orégano en maceta', '🌱', 'PLANTS', 'NEW', 220, false,
        'Tres macetas listas para la ventana de la casa. Con tierra preparada.')
    ];

    var interest = function (id, listingId, fromUserId, minutesAgo) {
      return { id: id, listingId: listingId, fromUserId: fromUserId, createdAt: NOW - minutesAgo * minute };
    };
    var interests = [
      interest('i1', 'M1', 'beto', 900),
      interest('i2', 'M2', 'beto', 880),
      interest('i3', 'B1', 'me', 860),
      interest('i4', 'M3', 'mari', 430),
      interest('i5', 'M6', 'me', 420),
      interest('i6', 'M7', 'me', 400),
      interest('i7', 'M3', 'fer', 3300),
      interest('i8', 'F1', 'me', 3280),
      interest('i9', 'M2', 'lucia', 210),
      interest('i10', 'M1', 'chuy', 150),
      interest('i11', 'M3', 'jorge', 70)
    ];

    var matches = [
      {
        id: 'x1', userA: 'me', userB: 'beto', status: 'CHAT', createdAt: NOW - 860 * minute,
        meeting: null, cashCents: 0, lastReadByMe: NOW - 30 * minute,
        messages: [
          { id: 'g1', fromUserId: 'system', kind: 'system', at: NOW - 860 * minute, text: 'Match de trueque: ambos marcaron interés. Ya pueden hablar sin compromiso.' },
          { id: 'g2', fromUserId: 'beto', kind: 'text', at: NOW - 820 * minute, text: 'Hola Ana. Vi que te interesa la bici y a mí tu mochila y la cafetera. ¿La bici te sirve para el campus o para ruta?' },
          { id: 'g3', fromUserId: 'me', kind: 'text', at: NOW - 700 * minute, text: 'Para el campus, pero de repente me voy al Volcán. ¿Los frenos aguantan?' },
          { id: 'g4', fromUserId: 'beto', kind: 'text', at: NOW - 26 * minute, text: 'Sí, los cambié hace un mes. Si quieres te la presto una tarde antes de cerrar el trato.' }
        ]
      },
      {
        id: 'x2', userA: 'me', userB: 'mari', status: 'CHAT', createdAt: NOW - 420 * minute,
        meeting: null, cashCents: 0, lastReadByMe: 0,
        messages: [
          { id: 'g5', fromUserId: 'system', kind: 'system', at: NOW - 420 * minute, text: 'Match de trueque: ambos marcaron interés. Ya pueden hablar sin compromiso.' },
          { id: 'g6', fromUserId: 'mari', kind: 'text', at: NOW - 380 * minute, text: 'Hola. Me interesa la bocina para los ensayos de Nutrición. La blusa es talla M y la conserva la hice esta semana.' }
        ]
      },
      {
        id: 'x3', userA: 'me', userB: 'fer', status: 'CERRADO', createdAt: NOW - 3280 * minute,
        meeting: 'Explanada de la facultad', cashCents: 0, lastReadByMe: NOW - 3000 * minute,
        messages: [
          { id: 'g7', fromUserId: 'system', kind: 'system', at: NOW - 3280 * minute, text: 'Match de trueque: ambos marcaron interés. Ya pueden hablar sin compromiso.' },
          { id: 'g8', fromUserId: 'fer', kind: 'text', at: NOW - 3200 * minute, text: 'Te cambio la guitarra por la bocina, si te late. Ya la probé, suena bien.' },
          { id: 'g9', fromUserId: 'me', kind: 'text', at: NOW - 3100 * minute, text: 'Va. Nos vemos en la explanada el jueves.' },
          { id: 'g10', fromUserId: 'system', kind: 'system', at: NOW - 3000 * minute, text: 'Trueque marcado como intercambiado en Explanada de la facultad.' }
        ]
      }
    ];

    return {
      version: 4,
      me: 'me',
      users: users,
      listings: listings,
      interests: interests,
      matches: matches,
      reports: []
    };
  }

  // ---------------------------------------------------------------------------------------------
  // Estado
  // ---------------------------------------------------------------------------------------------

  function load() {
    try {
      var raw = window.localStorage.getItem(STORE_KEY);
      if (raw) {
        var parsed = JSON.parse(raw);
        if (parsed && parsed.version === 4 && parsed.users && parsed.listings &&
            parsed.interests && parsed.matches && parsed.reports) {
          return parsed;
        }
      }
    } catch (error) {
      // datos corruptos: se vuelve a sembrar
    }
    for (var i = 0; i < LEGACY_KEYS.length; i++) {
      try { window.localStorage.removeItem(LEGACY_KEYS[i]); } catch (error) { /* sin storage */ }
    }
    return seed();
  }

  function save() {
    try { window.localStorage.setItem(STORE_KEY, JSON.stringify(data)); } catch (error) { /* sin espacio */ }
  }

  var data = load();

  var ui = {
    route: { name: 'feed' },
    filter: 'all',
    query: '',
    tradesTab: 'matches',
    giveId: null,
    getId: null,
    cash: '',
    chatDraft: '',
    editingProfile: false,
    confirmRetire: null,
    confirmBuy: null,
    report: null,
    syncResult: null,
    server: serverAvailable() ? 'idle' : 'offline',
    remoteHead: 0,
    form: freshForm()
  };

  var history = [];

  function freshForm() {
    return {
      title: '',
      description: '',
      emoji: '📦',
      category: 'CRAFTS',
      condition: 'GOOD',
      asking: '',
      barter: false
    };
  }

  function fallbackUser(id) {
    return { id: id, name: 'Compañero', phone: '', town: 'Colima', program: 'Alumnado', semester: '', rating: 5, trades: 0, since: '2025' };
  }

  function me() { return data.users[data.me] || fallbackUser(data.me); }
  function user(id) { return data.users[id] || fallbackUser(id); }

  function listing(id) {
    for (var i = 0; i < data.listings.length; i++) if (data.listings[i].id === id) return data.listings[i];
    return null;
  }

  function match(id) {
    for (var i = 0; i < data.matches.length; i++) if (data.matches[i].id === id) return data.matches[i];
    return null;
  }

  // ---------------------------------------------------------------------------------------------
  // Reglas del match
  // ---------------------------------------------------------------------------------------------

  function interestMark(listingId, userId) {
    for (var i = 0; i < data.interests.length; i++) {
      var mark = data.interests[i];
      if (mark.listingId === listingId && mark.fromUserId === userId) return mark;
    }
    return null;
  }

  function haveInterest(listingId) {
    return !!interestMark(listingId, data.me);
  }

  function wantedFromMe(otherId) {
    var items = [];
    data.interests.forEach(function (mark) {
      var item = listing(mark.listingId);
      if (mark.fromUserId === otherId && item && item.ownerId === data.me && item.barter) items.push(item);
    });
    return uniq(items);
  }

  function wantedByMe(otherId) {
    var items = [];
    data.interests.forEach(function (mark) {
      var item = listing(mark.listingId);
      if (mark.fromUserId === data.me && item && item.ownerId === otherId && item.barter) items.push(item);
    });
    return uniq(items);
  }

  function isMutual(otherId) {
    return wantedFromMe(otherId).length > 0 && wantedByMe(otherId).length > 0;
  }

  function matchWith(otherId) {
    for (var i = 0; i < data.matches.length; i++) {
      var item = data.matches[i];
      var pair = [item.userA, item.userB];
      if (pair.indexOf(data.me) >= 0 && pair.indexOf(otherId) >= 0) return item;
    }
    return null;
  }

  function otherOf(matchItem) {
    return matchItem.userA === data.me ? matchItem.userB : matchItem.userA;
  }

  function uniq(list) {
    var out = [];
    list.forEach(function (item) {
      if (!item) return;
      for (var i = 0; i < out.length; i++) if (out[i].id === item.id) return;
      out.push(item);
    });
    return out;
  }

  function reconcileMatches() {
    var created = [];
    Object.keys(data.users).forEach(function (otherId) {
      if (otherId === data.me) return;
      if (!isMutual(otherId)) return;
      if (matchWith(otherId)) return;

      var mine = wantedFromMe(otherId);
      var theirs = wantedByMe(otherId);
      var other = user(otherId);
      var item = {
        id: 'x_' + Date.now().toString(36) + '_' + otherId,
        userA: data.me,
        userB: otherId,
        status: 'CHAT',
        createdAt: Date.now(),
        meeting: null,
        cashCents: 0,
        lastReadByMe: 0,
        messages: [{
          id: 'g_' + Date.now().toString(36),
          fromUserId: 'system',
          kind: 'system',
          at: Date.now(),
          text: 'Match de trueque: ambos marcaron interés. Ya pueden hablar sin compromiso.'
        }]
      };
      data.matches.unshift(item);
      created.push({ match: item, mine: mine, theirs: theirs, other: other });
    });

    if (created.length) {
      save();
      created.forEach(function (entry) {
        var namesA = entry.mine.map(function (i) { return i.title; }).join(', ');
        var namesB = entry.theirs.map(function (i) { return i.title; }).join(', ');
        announce(
          'Match de trueque con ' + entry.other.name,
          entry.other.name + ' marcó interés en ' + namesA + ' y tú en ' + namesB + '. Ya pueden hablar.'
        );
      });
    }
    return created;
  }

  function lastMessageAt(matchItem) {
    var last = 0;
    matchItem.messages.forEach(function (message) { if (message.at > last) last = message.at; });
    return last;
  }

  function pendingCount() {
    return data.matches.filter(function (item) {
      if (item.status !== 'CHAT') return false;
      var theirs = item.messages.some(function (message) {
        return message.kind === 'text' && message.fromUserId !== data.me;
      });
      return theirs && (item.lastReadByMe || 0) < lastMessageAt(item);
    }).length;
  }

  function myInterestMarks() {
    return data.interests.filter(function (mark) {
      var item = listing(mark.listingId);
      return mark.fromUserId === data.me && item && item.ownerId !== data.me;
    });
  }

  function receivedInterestStats() {
    var people = {};
    var items = {};
    data.interests.forEach(function (mark) {
      var item = listing(mark.listingId);
      if (!item || item.ownerId !== data.me) return;
      people[mark.fromUserId] = true;
      items[item.id] = true;
    });
    var ids = Object.keys(people);
    return {
      people: ids.length,
      items: Object.keys(items).length,
      hidden: ids.filter(function (id) { return !matchWith(id); }).length
    };
  }

  function statusLabel(status) {
    if (status === 'CHAT') return 'Chat abierto';
    if (status === 'ACORDADO') return 'Trato acordado';
    if (status === 'CERRADO') return 'Intercambiado';
    if (status === 'ARCHIVADO') return 'Sin trato';
    return status;
  }

  function statusClass(status) {
    if (status === 'CHAT') return 'pendiente';
    if (status === 'ACORDADO') return 'aceptado';
    if (status === 'CERRADO') return 'cerrado';
    if (status === 'ARCHIVADO') return 'cancelado';
    return String(status || '').toLowerCase();
  }

  function announce(title, body) {
    try {
      if (window.Nahua && typeof Nahua.announce === 'function') {
        var pending = Nahua.announce({ title: title, body: body });
        if (pending && pending.catch) pending.catch(function () { flash(title); });
        return;
      }
    } catch (error) {
      // Sin anfitrión el aviso queda en el flujo local.
    }
  }

  function flash(text) {
    var host = document.getElementById('flash');
    if (!host) return;
    var element = document.createElement('div');
    element.className = 'flash-item';
    element.textContent = text;
    host.appendChild(element);
    while (host.children.length > 2) host.removeChild(host.firstChild);
    window.setTimeout(function () {
      if (element.parentNode) element.parentNode.removeChild(element);
    }, 2600);
  }

  // ---------------------------------------------------------------------------------------------
  // Piezas compartidas
  // ---------------------------------------------------------------------------------------------

  function avatar(userId, size) {
    var profile = user(userId);
    return '<span class="avatar ' + (size || '') + '" style="background:' + avatarColor(userId) + '">' +
      esc(initials(profile.name)) + '</span>';
  }

  function backButton(label, route) {
    return '<button class="back" data-act="back" data-id="' + esc(route || '') + '">← ' + esc(label) + '</button>';
  }

  function stat(value, label) {
    return '<div class="stat"><strong>' + value + '</strong><span>' + esc(label) + '</span></div>';
  }

  function storeBanner(store, options) {
    var settings = options || {};
    var list = productsForStore(store.id).slice(0, 3);
    var emojis = list.map(function (product) { return '<span class="glyph">' + product.emoji + '</span>'; }).join('');
    var count = productsForStore(store.id).length;

    return '<button class="store-banner" data-act="' + (settings.act || 'store') + '" data-id="' + store.id +
      '" style="--accent:' + store.accent + ';--deep:' + store.deep + '">' +
      '<div class="banner-copy">' +
        '<span class="banner-kicker">Tienda oficial · desde ' + esc(store.since) + '</span>' +
        '<strong class="banner-name">' + esc(store.name) + '</strong>' +
        '<span class="banner-offer">' + esc(store.offer) + '</span>' +
        '<span class="banner-cta">Ver tienda · ' + count + ' productos →</span>' +
      '</div>' +
      '<div class="banner-art">' + emojis + '</div>' +
      '<span class="banner-stamp">' + store.emoji + '</span>' +
    '</button>';
  }

  function productCard(product, options) {
    var settings = options || {};
    var store = storeById(product.storeId);
    var stock = localStock(product);
    var soldOut = stock <= 0;
    var bought = !!purchases[product.id];
    var status = catalogStatus();
    var tag = product.tag ? '<span class="badge">' + esc(product.tag) + '</span>' : '';

    return '<article class="product-card' + (soldOut ? ' sold-out' : '') + '">' +
      '<div class="product-art" style="background:' + store.accent + '22;border-color:' + store.accent + '55">' +
        '<span class="product-emoji">' + product.emoji + '</span>' +
        (soldOut ? '<span class="stock-flag">Agotado</span>' : '') +
      '</div>' +
      '<div class="product-body">' +
        '<div class="product-head"><h3>' + esc(product.title) + '</h3>' + tag + '</div>' +
        '<div class="product-store">' + esc(store.short) + ' · ' +
          (soldOut ? 'sin stock' : stock + (stock === 1 ? ' disponible' : ' disponibles')) +
        '</div>' +
        '<div class="product-foot"><span class="price">' + money(product.priceCents) + '</span>' +
          (settings.readOnly
            ? '<span class="badge badge-ghost">Vista histórica</span>'
            : (soldOut
              ? '<button class="btn sm" disabled>Agotado</button>'
              : '<button class="btn sm primary" data-act="buy-official" data-id="' + product.id + '">Comprar</button>')) +
        '</div>' +
        (status.stale && !settings.readOnly
          ? '<div class="stock-warn">Catálogo ' + esc(status.label.toLowerCase()) + ' · puede faltar stock</div>'
          : '') +
      '</div>' +
    '</article>';
  }

  function productRow(product) {
    var store = storeById(product.storeId);
    return '<div class="product-row">' +
      '<span class="row-emoji">' + product.emoji + '</span>' +
      '<div class="row-body"><b>' + esc(product.title) + '</b><small>' + esc(store.short) + ' · ' +
        (product.stock > 0 ? product.stock + ' en stock' : 'agotado') + '</small></div>' +
      '<span class="row-price">' + money(product.priceCents) + '</span>' +
    '</div>';
  }

  // ---------------------------------------------------------------------------------------------
  // Vistas: bazar
  // ---------------------------------------------------------------------------------------------

  function filteredListings() {
    var query = ui.query.trim().toLowerCase();
    var items = data.listings.filter(function (item) {
      if (item.status === 'VENDIDO' || item.status === 'TRUEQUEADO' || item.status === 'OCULTO') return false;
      if (ui.filter === 'barter' && !(item.barter && item.status === 'DISPONIBLE')) return false;
      if (ui.filter === 'venta' && item.barter) return false;
      if (ui.filter === 'mine' && item.ownerId !== data.me) return false;
      if (!query) return true;
      var owner = user(item.ownerId);
      var haystack = [item.title, item.description, categoryById(item.category).label, owner.name, owner.town]
        .join(' ').toLowerCase();
      return haystack.indexOf(query) >= 0;
    });
    return items.sort(function (a, b) { return (b.createdAt || 0) - (a.createdAt || 0); });
  }

  function listTitle() {
    if (ui.filter === 'barter') return 'Objetos en trueque';
    if (ui.filter === 'venta') return 'Sólo venta';
    if (ui.filter === 'mine') return 'Mis publicaciones';
    return 'Lo nuevo de la comunidad';
  }

  function filterChips() {
    var options = [
      { id: 'all', label: 'Todo' },
      { id: 'barter', label: 'Acepta trueque' },
      { id: 'venta', label: 'Sólo venta' },
      { id: 'mine', label: 'Mío' }
    ];
    return '<div class="chips">' + options.map(function (option) {
      return '<button class="chip' + (ui.filter === option.id ? ' active' : '') + '" data-act="filter" data-id="' +
        option.id + '">' + esc(option.label) + '</button>';
    }).join('') + '</div>';
  }

  function interestButton(item) {
    var marked = haveInterest(item.id);
    return '<button class="btn ' + (marked ? 'green' : 'rose') + ' sm" data-act="interest" data-id="' + item.id + '">' +
      (marked ? '✓ Te interesa' : '♡ Me interesa') + '</button>';
  }

  function card(item) {
    var owner = user(item.ownerId);
    var mine = item.ownerId === data.me;
    var canInterest = item.barter && !mine && item.status === 'DISPONIBLE';
    var matched = canInterest && isMutual(owner.id);
    var statusBadge = '';
    if (item.status === 'RESERVADO') statusBadge = '<span class="badge badge-done">Apartado</span>';
    if (item.reportedByMe) statusBadge += '<span class="badge badge-report">Reportado</span>';

    return '<article class="item-card" data-act="open" data-id="' + item.id + '">' +
      '<div class="thumb">' + item.emoji + (item.barter ? '<span class="flag">Trueque</span>' : '') + '</div>' +
      '<div class="item-body">' +
        '<h3>' + esc(item.title) + '</h3>' +
        '<div class="item-meta">' + esc(conditionById(item.condition).short) + ' · ' + esc(owner.name) +
          ' · ' + esc(owner.town) + '</div>' +
        '<div class="item-values"><div class="ask">' + money(item.askingCents) + '<small>precio</small></div></div>' +
        '<div class="item-actions">' +
          (mine ? '<span class="badge badge-mine">Tu publicación</span>' : '') +
          (item.barter ? '<span class="badge badge-barter">Acepta trueque</span>' : '<span class="badge badge-sale">Sólo venta</span>') +
          statusBadge +
          (matched ? '<span class="badge badge-match">Match</span>' : '') +
          (canInterest ? interestButton(item) : '') +
        '</div>' +
      '</div>' +
    '</article>';
  }

  function cardsHtml() {
    var items = filteredListings();
    if (!items.length) {
      return '<div class="empty"><div class="ico">🧺</div><h3>Nada por aquí todavía</h3>' +
        '<p>Cambia el filtro o publica el primer objeto de tu comunidad.</p>' +
        '<button class="btn primary" data-act="tab" data-id="publish">Publicar un objeto</button></div>';
    }
    return items.map(card).join('');
  }

  function feedView() {
    // Sin bloque de texto: la portada son los banners de las tiendas oficiales.
    return '<div class="banner-rail">' + storeList().map(function (store) {
        return storeBanner(store);
      }).join('') + '</div>' +
      freshnessChip('block-chip') +
      '<div class="search"><span>⌕</span><input id="q" type="search" placeholder="Busca objeto, comunidad o persona" value="' + esc(ui.query) + '"></div>' +
      filterChips() +
      '<div class="section-head"><h2 id="listTitle">' + esc(listTitle()) + '</h2><span id="listCount"></span></div>' +
      '<div class="cards" id="cards">' + cardsHtml() + '</div>' +
      '<p class="footer-note">El precio lo pone quien publica. El bazar no valora objetos.</p>';
  }

  function contactRow(userId) {
    var profile = user(userId);
    var phone = formatPhone(profile.phone);
    return '<div class="contact">' +
      '<div class="contact-ico">☎</div>' +
      '<div class="contact-body">' +
        '<span class="contact-lbl">Contacto</span>' +
        '<b class="tabular">' + esc(phone || 'sin teléfono') + '</b>' +
        '<small>' + esc(profile.name) + '</small>' +
      '</div>' +
      (phone ? '<button class="btn sm" data-act="copy-phone" data-id="' + esc(profile.id) + '">Copiar</button>' : '') +
    '</div>';
  }

  function ownerRow(userId) {
    var profile = user(userId);
    var mine = userId === data.me;
    return '<button class="owner" data-act="profile" data-id="' + profile.id + '">' +
      avatar(userId) +
      '<div>' +
        '<div class="owner-name">' + esc(profile.name) + (mine ? ' (tú)' : '') + '</div>' +
        '<div class="owner-sub">' + esc(profile.town) + ' · ' + esc(profile.program) + ' · ★ ' +
          profile.rating.toFixed(1) + ' · ' + profile.trades + ' tratos</div>' +
      '</div>' +
      '<span class="go">›</span>' +
    '</button>';
  }

  function reportPanel(targetType, targetId) {
    var report = ui.report;
    if (!report || report.type !== targetType || report.id !== targetId) return '';

    var reasons = REPORT_REASONS.map(function (reason) {
      return '<button class="option' + (report.reason === reason.id ? ' active' : '') +
        '" data-act="report-reason" data-id="' + reason.id + '">' + esc(reason.label) + '</button>';
    }).join('');

    return '<div class="panel report-panel">' +
      '<div class="panel-head"><h3>Reportar</h3><span class="conf">confidencial</span></div>' +
      '<p class="report-lede">Cuéntanos qué pasó. El reporte se guarda con fecha y se manda a revisión.</p>' +
      '<div class="option-grid">' + reasons + '</div>' +
      '<div class="field"><label>Detalle (opcional)</label>' +
        '<textarea id="reportNote" maxlength="240" placeholder="Qué pasó, cuándo y qué esperabas">' +
          esc(report.note || '') + '</textarea></div>' +
      '<div class="btn-row">' +
        '<button class="btn danger" data-act="report-send">Enviar reporte</button>' +
        '<button class="btn" data-act="report-cancel">Cancelar</button>' +
      '</div>' +
    '</div>';
  }

  function itemView(id) {
    var item = listing(id);
    if (!item) return notFound('Ese objeto ya no está en el bazar.');
    var owner = user(item.ownerId);
    var mine = item.ownerId === data.me;
    var available = item.status === 'DISPONIBLE';
    var mutual = !mine && available && isMutual(owner.id);
    var actions = '';

    if (!mine && available) {
      if (item.barter) {
        var marked = haveInterest(item.id);
        actions += interestButton(item);
        actions += '<div class="hint-line" style="margin-top:8px">' +
          (marked
            ? 'Tu interés es privado. Si ' + esc(owner.name.split(' ')[0]) + ' marca algo tuyo, se abre el chat.'
            : 'Marcar no avisa a nadie: tu interés es privado y sólo se abre el chat si ' +
              esc(owner.name.split(' ')[0]) + ' también marca algo tuyo.') + '</div>';
      }
      actions += '<button class="btn primary block" data-act="buy" data-id="' + item.id + '">Comprar con cartera · ' +
        money(item.askingCents) + '</button>';
      actions += '<button class="btn danger block" data-act="report-open" data-id="' + item.id + '">⚠ Reportar artículo</button>';
    } else if (mine) {
      actions += '<button class="badge badge-mine" style="align-self:flex-start">Tu publicación</button>';
      if (item.barter) {
        var wants = 0;
        data.interests.forEach(function (mark) {
          if (mark.listingId === item.id && mark.fromUserId !== data.me) wants += 1;
        });
        actions += '<div class="note">' + (wants === 0
          ? 'Nadie ha marcado interés todavía. El interés es privado: no verás nombres hasta que sea mutuo.'
          : wants + (wants === 1 ? ' persona marcó' : ' personas marcaron') +
            ' interés en este objeto. Los nombres se revelan sólo cuando también te interesa algo suyo.') + '</div>';
      }
      actions += '<button class="btn primary block" data-act="tab" data-id="trades">Ver mis trueques</button>';
      if (item.status === 'DISPONIBLE' && ui.confirmRetire === item.id) {
        actions += '<div class="note">¿Retirar «' + esc(item.title) + '» del bazar? Dejará de aparecer en el feed.</div>';
        actions += '<div class="btn-row">' +
          '<button class="btn danger" data-act="retire-confirm" data-id="' + item.id + '">Sí, retirar</button>' +
          '<button class="btn" data-act="retire-cancel">Mejor no</button>' +
        '</div>';
      } else if (item.status === 'DISPONIBLE') {
        actions += '<button class="btn block" data-act="retire" data-id="' + item.id + '">Retirar del bazar</button>';
      }
    } else {
      actions += '<button class="btn block" data-act="tab" data-id="feed">Ver otros objetos</button>';
    }

    var mutualBlock = mutual ? '<div class="panel" style="margin-top:14px;background:var(--green-soft);border-color:#B9D2AA">' +
      '<div class="panel-head"><h3>Match de trueque</h3></div>' +
      '<p class="match-lede">Tú y ' + esc(owner.name.split(' ')[0]) + ' se interesan mutuamente. ' +
        'Pueden hablar y cerrar el trato cuando quieran.</p>' +
      '<button class="btn green block" data-act="open-match" data-id="' + owner.id + '">Abrir chat de trueque</button>' +
      '</div>' : '';

    return backButton('Bazar', 'feed') +
      '<div class="hero-art">' + item.emoji +
        (item.barter ? '<span class="ribbon">Acepta trueque</span>' : '<span class="ribbon" style="background:var(--blue)">Sólo venta</span>') +
      '</div>' +
      '<h1 class="detail-title">' + esc(item.title) + '</h1>' +
      '<div class="detail-meta">' + esc(categoryById(item.category).label) + ' · ' +
        esc(conditionById(item.condition).label) + '</div>' +
      '<div class="price-row">' +
        '<div class="price-main tabular">' + money(item.askingCents) + '<small>precio que pide</small></div>' +
        '<span class="badge badge-sale">Precio del dueño</span>' +
      '</div>' +
      (item.description ? '<p class="desc">' + esc(item.description) + '</p>' : '') +
      (item.barter
        ? '<div class="hint-line" style="margin-top:10px">Trueque con match: ' + esc(owner.name) +
          ' no ve tu interés hasta que ambos marquen algo del otro.</div>'
        : '<div class="note">Este objeto no acepta trueque, pero puedes comprarlo con tu cartera Nahua.</div>') +
      mutualBlock +
      '<div class="actions-block">' + actions + '</div>' +
      reportPanel('listing', item.id) +
      '<div style="margin-top:12px">' + ownerRow(item.ownerId) + '</div>';
  }

  function tradesView() {
    var matches = data.matches;
    var marks = myInterestMarks();
    var received = receivedInterestStats();
    var tab = ui.tradesTab === 'marks' ? 'marks' : (ui.tradesTab === 'received' ? 'received' : 'matches');

    var body = '';
    if (tab === 'matches') {
      body = matches.length
        ? matches.map(matchCard).join('')
        : '<div class="empty"><div class="ico">⇄</div><h3>Aún no hay match</h3>' +
          '<p>Marca lo que te interesa en el bazar. Cuando alguien marque algo tuyo, el chat se abre solo.</p>' +
          '<button class="btn primary" data-act="tab" data-id="feed">Ver el bazar</button></div>';
    } else if (tab === 'marks') {
      body = marks.length
        ? marks.map(markCard).join('')
        : '<div class="empty"><div class="ico">♡</div><h3>No has marcado nada</h3>' +
          '<p>En cada objeto con trueque hay un botón «Me interesa». Es privado.</p>' +
          '<button class="btn primary" data-act="tab" data-id="feed">Ver el bazar</button></div>';
    } else {
      body = '<div class="panel">' +
        '<div class="panel-head"><h3>Interés en lo mío</h3></div>' +
        '<div class="value-big tabular">' + received.people + '</div>' +
        '<div class="value-range">' + (received.people === 1 ? 'persona marcó' : 'personas marcaron') +
          ' ' + received.items + ' de tus objetos</div>' +
        '<div class="hint-line" style="margin-top:10px">Los nombres se revelan sólo cuando tú también ' +
          'marcaste algo suyo. Mientras tanto el interés es anónimo: nadie te debe un trueque.</div>' +
        (received.hidden > 0 ? '<div class="note">' + received.hidden +
          ' de esas personas todavía no tiene match contigo.</div>' : '') +
      '</div>' +
      '<div class="section-head"><h2>Tus objetos en trueque</h2><span>' + myBarterables().length + '</span></div>' +
      '<div class="cards">' + (myBarterables().length ? myBarterables().map(function (item) {
        var people = 0;
        data.interests.forEach(function (mark) {
          if (mark.listingId === item.id && mark.fromUserId !== data.me) people += 1;
        });
        return '<article class="item-card" data-act="open" data-id="' + item.id + '">' +
          '<div class="thumb">' + item.emoji + '<span class="flag">Trueque</span></div>' +
          '<div class="item-body"><h3>' + esc(item.title) + '</h3>' +
            '<div class="item-meta">' + money(item.askingCents) + ' · ' +
              (people === 0 ? 'sin interés todavía' : people + (people === 1 ? ' interesado' : ' interesados')) +
            '</div></div></article>';
      }).join('') : '<div class="empty"><p>Publica algo con trueque para empezar.</p></div>') + '</div>';
    }

    return '<p class="kicker">Tratos</p>' +
      '<h1 style="font-size:20px;margin-top:4px">Trueques con match</h1>' +
      '<p class="lede" style="margin-bottom:0">El chat y el contacto se abren sólo cuando ambos se interesan.</p>' +
      '<div class="segmented">' +
        '<button class="' + (tab === 'matches' ? 'active' : '') + '" data-act="trades-tab" data-id="matches">Matches (' + matches.length + ')</button>' +
        '<button class="' + (tab === 'marks' ? 'active' : '') + '" data-act="trades-tab" data-id="marks">Mis marcas (' + marks.length + ')</button>' +
        '<button class="' + (tab === 'received' ? 'active' : '') + '" data-act="trades-tab" data-id="received">En lo mío (' + received.people + ')</button>' +
      '</div>' +
      '<div class="cards" style="margin-top:12px">' + body + '</div>';
  }

  function matchCard(item) {
    var other = user(otherOf(item));
    var unread = (item.lastReadByMe || 0) < lastMessageAt(item);
    var last = '';
    for (var i = item.messages.length - 1; i >= 0; i--) {
      if (item.messages[i].kind === 'text') { last = item.messages[i].text; break; }
    }
    var mineCount = wantedFromMe(other.id).length;
    var theirsCount = wantedByMe(other.id).length;

    return '<article class="offer-card" data-act="match" data-id="' + item.id + '">' +
      '<div class="offer-top">' + avatar(other.id, '') +
        '<div class="t">Match con <b>' + esc(other.name) + '</b>' +
          (unread ? '<span class="dot"></span>' : '') + '</div>' +
      '</div>' +
      '<div class="match-counts">' +
        '<span>♡ ' + theirsCount + ' te interesan</span>' +
        '<span>♡ ' + mineCount + ' le interesan de ti</span>' +
      '</div>' +
      (last ? '<div class="last-msg">“' + esc(last.length > 78 ? last.slice(0, 78) + '…' : last) + '”</div>' : '') +
      '<div class="offer-foot">' +
        '<span class="pill ' + statusClass(item.status) + '">' + esc(statusLabel(item.status)) + '</span>' +
        '<span style="margin-left:auto;font-size:10px;color:var(--muted)">' + esc(ago(item.createdAt)) + '</span>' +
      '</div>' +
    '</article>';
  }

  function markCard(mark) {
    var item = listing(mark.listingId);
    if (!item) return '';
    var owner = user(item.ownerId);
    var mutual = isMutual(owner.id);
    return '<article class="offer-card" data-act="open" data-id="' + item.id + '">' +
      '<div class="offer-top">' + avatar(owner.id, '') +
        '<div class="t">Te interesa <b>' + esc(item.title) + '</b> de ' + esc(owner.name) + '</div>' +
      '</div>' +
      '<div class="swap-row">' +
        '<div class="swap-item"><span class="lbl">Precio</span><span class="e">' + item.emoji + '</span>' +
          '<b>' + money(item.askingCents) + '</b><small>' + esc(categoryById(item.category).label) + '</small></div>' +
        '<div class="swap-arrow">' + (mutual ? '⇄' : '…') + '</div>' +
        '<div class="swap-item"><span class="lbl">Estado</span><span class="e">' + (mutual ? '✓' : '⏳') + '</span>' +
          '<b>' + (mutual ? 'Match: ya hay chat' : 'Esperando reciprocidad') + '</b>' +
          '<small>' + esc(owner.town) + '</small></div>' +
      '</div>' +
      '<div class="offer-foot"><span class="pill ' + (mutual ? 'aceptado' : 'pendiente') + '">' +
        (mutual ? 'Chat abierto' : 'Interés privado') + '</span></div>' +
    '</article>';
  }

  function selectedPair(matchItem) {
    var otherId = otherOf(matchItem);
    var mine = wantedFromMe(otherId);
    var theirs = wantedByMe(otherId);
    var give = null;
    var get = null;
    for (var i = 0; i < mine.length; i++) if (mine[i].id === ui.giveId) give = mine[i];
    for (var j = 0; j < theirs.length; j++) if (theirs[j].id === ui.getId) get = theirs[j];
    return { mine: mine, theirs: theirs, give: give || mine[0] || null, get: get || theirs[0] || null };
  }

  function matchView(id) {
    var item = match(id);
    if (!item) return notFound('Ese match ya no existe.');
    var other = user(otherOf(item));
    var pair = selectedPair(item);
    var open = item.status === 'CHAT';

    var chat = item.messages.map(function (message) {
      if (message.kind === 'system') return '<div class="chat-sys">' + esc(message.text) + '</div>';
      var mine = message.fromUserId === data.me;
      return '<div class="bubble ' + (mine ? 'me' : 'them') + '">' + esc(message.text) +
        '<span class="at">' + esc(ago(message.at)) + '</span></div>';
    }).join('');

    var pairOptions = function (list, act, picked) {
      return '<div class="option-grid">' + list.map(function (entry) {
        return '<button class="option' + (picked && entry.id === picked.id ? ' active' : '') + '" data-act="' +
          act + '" data-id="' + entry.id + '">' + entry.emoji + ' ' + esc(entry.title) + '</button>';
      }).join('') + '</div>';
    };

    var body = backButton('Trueques', 'trades') +
      '<div class="offer-foot"><span class="pill ' + statusClass(item.status) + '">' +
        esc(statusLabel(item.status)) + '</span>' +
        '<span style="font-size:10px;color:var(--muted)">' + esc(ago(item.createdAt)) + '</span></div>' +
      '<h1 class="detail-title">Match con ' + esc(other.name) + '</h1>' +
      '<p class="detail-meta">' + esc(other.town) + ' · ' + esc(other.program) + ' · ' + esc(other.semester) + '</p>' +
      contactRow(other.id) +
      '<div class="section-head"><h2>Interés mutuo</h2><span>' + pair.mine.length + ' ⇄ ' + pair.theirs.length + '</span></div>' +
      (open
        ? '<div class="field"><label>Das</label>' + pairOptions(pair.mine, 'pick-give', pair.give) + '</div>' +
          '<div class="field"><label>Recibes</label>' + pairOptions(pair.theirs, 'pick-get', pair.get) + '</div>'
        : '<div class="deal-grid">' +
            '<div class="deal-box"><span class="l">Das</span><span class="e">' + (pair.give ? pair.give.emoji : '📦') + '</span>' +
              '<span class="n">' + esc(pair.give ? pair.give.title : '—') + '</span>' +
              '<span class="v tabular">' + money(pair.give ? pair.give.askingCents : 0) + '</span></div>' +
            '<div class="deal-vs">⇄</div>' +
            '<div class="deal-box"><span class="l">Recibes</span><span class="e">' + (pair.get ? pair.get.emoji : '📦') + '</span>' +
              '<span class="n">' + esc(pair.get ? pair.get.title : '—') + '</span>' +
              '<span class="v tabular">' + money(pair.get ? pair.get.askingCents : 0) + '</span></div>' +
          '</div>') +
      (pair.give && pair.get
        ? '<div class="value-pair">' +
            '<div><span>Das · tu precio</span><b>' + money(pair.give.askingCents) + '</b><small>' + esc(pair.give.title) + '</small></div>' +
            '<div><span>Recibes · su precio</span><b>' + money(pair.get.askingCents) + '</b><small>' + esc(pair.get.title) + '</small></div>' +
          '</div>'
        : '') +
      (item.cashCents > 0 ? '<div class="panel" style="margin-top:10px;background:var(--gold-soft);border-color:#E6C58A">' +
        '<div class="panel-head"><h3>Efectivo acordado</h3></div>' +
        '<div class="value-big tabular">' + money(item.cashCents) + '</div>' +
        '<div class="value-range">Se suma al intercambio.</div></div>' : '') +
      '<div class="section-head"><h2>Chat del trueque</h2><span>' + item.messages.length + '</span></div>' +
      '<div class="chat" id="chatLog">' + chat + '</div>' +
      (open
        ? '<div class="chat-bar">' +
            '<input id="chatInput" type="text" maxlength="240" placeholder="Escribe sin compromiso…" value="' + esc(ui.chatDraft) + '">' +
            '<button class="btn rose" data-act="send-msg" data-id="' + item.id + '">Enviar</button>' +
          '</div>'
        : '') +
      '<div class="actions-block">';

    if (item.status === 'CHAT') {
      body += '<div class="field"><label>Punto de encuentro</label>' +
        '<select id="meetingSelect">' + MEETING_POINTS.map(function (point) {
          return '<option' + (item.meeting === point ? ' selected' : '') + '>' + esc(point) + '</option>';
        }).join('') + '</select>' +
        '<div class="help">Lugar público y con gente del campus.</div></div>' +
        '<div class="field"><label>Efectivo de un lado (opcional)</label>' +
          '<input id="cashInput" type="number" inputmode="numeric" placeholder="Ej. 250" value="' + esc(ui.cash) + '">' +
          '<div class="help">Sólo si ustedes lo acuerdan. Pesos, sin centavos.</div></div>' +
        '<button class="btn green block" data-act="agree" data-id="' + item.id + '">✓ Acordar trueque</button>' +
        '<button class="btn danger block" data-act="archive" data-id="' + item.id + '">Retirar mi interés</button>' +
        '<button class="btn danger block" data-act="report-user" data-id="' + other.id + '">⚠ Reportar a ' +
          esc(other.name.split(' ')[0]) + '</button>';
    } else if (item.status === 'ACORDADO') {
      body += '<div class="panel" style="background:var(--green-soft);border-color:#B9D2AA">' +
        '<div class="panel-head"><h3>Trato acordado</h3></div>' +
        '<ol class="steps">' +
          '<li>Acuerden día y hora en el chat.</li>' +
          '<li>Revisen los dos objetos antes de cerrar.</li>' +
          '<li>Marquen el trueque como intercambiado al terminar.</li>' +
        '</ol></div>' +
        '<button class="btn green block" data-act="complete" data-id="' + item.id + '">Marcar como intercambiado</button>' +
        '<button class="btn danger block" data-act="archive" data-id="' + item.id + '">Cancelar trato</button>';
    } else if (item.status === 'CERRADO') {
      body += '<div class="panel" style="background:var(--green-soft);border-color:#B9D2AA;text-align:center">' +
        '<div style="font-size:30px">🤝</div>' +
        '<h3 style="margin-top:6px">Trueque completado</h3>' +
        (item.meeting ? '<p style="font-size:11px;color:var(--muted);margin-top:6px">' + esc(item.meeting) + '</p>' : '') +
      '</div>' +
      '<button class="btn primary block" data-act="tab" data-id="feed">Seguir en el bazar</button>';
    } else {
      body += '<div class="note">Este match quedó sin trato. Los objetos siguen publicados y el chat queda como historial.</div>' +
        '<button class="btn primary block" data-act="tab" data-id="feed">Ver el bazar</button>';
    }

    body += '</div>' + reportPanel('user', other.id);
    return body;
  }

  function profileView(id) {
    var profile = user(id);
    var mine = id === data.me;
    var items = data.listings.filter(function (item) {
      return item.ownerId === id && item.status !== 'OCULTO';
    });
    var barterables = items.filter(function (item) {
      return item.barter && item.status === 'DISPONIBLE';
    });
    var editing = mine && ui.editingProfile;

    var header = '<div class="profile-head">' + avatar(id, 'lg') +
        '<div><h2>' + esc(profile.name) + (mine ? ' (tú)' : '') + '</h2>' +
          '<div class="p-sub">' + esc(profile.program) + ' · ' + esc(profile.semester) + '</div>' +
          '<div class="p-sub">' + esc(profile.town) + ' · desde ' + esc(profile.since || '2025') + '</div>' +
        '</div>' +
      '</div>';

    var contact = editing
      ? '<div class="panel">' +
          '<div class="panel-head"><h3>Datos de contacto</h3></div>' +
          '<div class="field"><label>Nombre</label><input id="pName" type="text" maxlength="40" value="' + esc(profile.name) + '"></div>' +
          '<div class="field"><label>Teléfono (WhatsApp)</label><input id="pPhone" type="tel" inputmode="numeric" maxlength="14" placeholder="312 123 4567" value="' +
            esc(formatPhone(profile.phone)) + '"><div class="help">10 dígitos. Se comparte sólo cuando hay match.</div></div>' +
          '<div class="field"><label>Comunidad o municipio</label><input id="pTown" type="text" maxlength="40" value="' + esc(profile.town) + '"></div>' +
          '<div class="btn-row" style="margin-top:10px">' +
            '<button class="btn green" data-act="save-profile">Guardar</button>' +
            '<button class="btn" data-act="edit-profile">Cancelar</button>' +
          '</div>' +
        '</div>'
      : contactRow(id) +
        (mine
          ? '<button class="btn block" style="margin-top:8px" data-act="edit-profile">Editar nombre y teléfono</button>'
          : '<button class="btn danger block" style="margin-top:8px" data-act="report-user" data-id="' + profile.id + '">⚠ Reportar usuario</button>');

    var cards = items.length
      ? items.map(function (item) {
          var canInterest = !mine && item.barter && item.status === 'DISPONIBLE';
          return '<article class="item-card" data-act="open" data-id="' + item.id + '">' +
            '<div class="thumb">' + item.emoji + (item.barter ? '<span class="flag">Trueque</span>' : '') + '</div>' +
            '<div class="item-body"><h3>' + esc(item.title) + '</h3>' +
              '<div class="item-meta">' + esc(conditionById(item.condition).short) + ' · ' + money(item.askingCents) + '</div>' +
              '<div class="item-actions">' +
                (item.barter ? '<span class="badge badge-barter">Acepta trueque</span>' : '<span class="badge badge-sale">Sólo venta</span>') +
                (canInterest ? interestButton(item) : '') +
              '</div></div></article>';
        }).join('')
      : '<div class="empty"><div class="ico">📦</div><h3>' + (mine ? 'Aún no publicas nada' : 'Sin objetos publicados') + '</h3>' +
        '<p>' + (mine ? 'Publica tu primer objeto y decide si lo vendes o lo abres a trueque.' : 'Esta persona no tiene objetos disponibles.') + '</p>' +
        (mine ? '<button class="btn primary" data-act="tab" data-id="publish">Publicar un objeto</button>' : '') + '</div>';

    return backButton(mine ? 'Bazar' : 'Volver', 'feed') +
      header +
      '<div class="profile-stats">' +
        stat(items.length, 'objetos') +
        stat(barterables.length, 'en trueque') +
        stat(profile.trades, 'tratos') +
      '</div>' +
      '<div style="margin-top:12px">' + contact + '</div>' +
      (mine
        ? '<div class="note">Así te ven los demás. Tu teléfono no aparece en el bazar: sólo en un chat con match. ' +
          'Reportes enviados: ' + data.reports.length + '.</div>'
        : '') +
      reportPanel('user', id) +
      '<div class="section-head"><h2>' + (mine ? 'Mi bazar' : 'Su bazar') + '</h2><span>' + barterables.length + ' en trueque</span></div>' +
      '<div class="cards">' + cards + '</div>';
  }

  function publishView() {
    var form = ui.form;

    var emojis = EMOJIS.map(function (emoji) {
      return '<button class="emoji-option' + (form.emoji === emoji ? ' active' : '') + '" data-act="set-emoji" data-id="' +
        emoji + '">' + emoji + '</button>';
    }).join('');

    var categories = CATEGORIES.map(function (category) {
      return '<button class="option' + (form.category === category.id ? ' active' : '') + '" data-act="set-cat" data-id="' +
        category.id + '">' + category.emoji + ' ' + esc(category.label) + '</button>';
    }).join('');

    var conditions = CONDITIONS.map(function (condition) {
      return '<button class="option' + (form.condition === condition.id ? ' active' : '') + '" data-act="set-cond" data-id="' +
        condition.id + '">' + esc(condition.short) + '</button>';
    }).join('');

    return '<p class="kicker">Publicar</p>' +
      '<h1 style="font-size:21px;margin-top:4px">Pon tu objeto en el bazar</h1>' +
      '<p class="lede">Tú pones el precio. El bazar no valora objetos ni sugiere precios.</p>' +

      '<div class="field"><label>Título</label>' +
        '<input id="fTitle" type="text" maxlength="60" placeholder="Ej. Blusa bordada de Suchitlán" value="' + esc(form.title) + '"></div>' +

      '<div class="field"><label>Descripción</label>' +
        '<textarea id="fDesc" maxlength="240" placeholder="Estado real, qué incluye, de qué comunidad viene">' + esc(form.description) + '</textarea></div>' +

      '<div class="field"><label>Ícono</label><div class="emoji-grid">' + emojis + '</div></div>' +

      '<div class="field"><label>Categoría</label><div class="option-grid">' + categories + '</div></div>' +

      '<div class="field"><label>Estado</label><div class="option-grid">' + conditions + '</div></div>' +

      '<div class="field"><label>Precio en pesos</label>' +
        '<input id="fAsk" type="number" inputmode="numeric" placeholder="Ej. 700" value="' + esc(form.asking) + '">' +
        '<div class="help">El precio lo decides tú. Nadie más lo calcula.</div></div>' +

      '<div class="switch-row" style="margin-top:14px">' +
        '<div class="txt"><strong>Acepto trueque con match</strong>' +
          '<span>Se abre chat sólo cuando haya interés de las dos partes</span></div>' +
        '<button class="switch' + (form.barter ? ' on' : '') +
          '" data-act="toggle-barter" aria-label="Acepto trueque"></button>' +
      '</div>' +

      '<div class="actions-block">' +
        '<button class="btn primary block" data-act="publish">Publicar en el bazar</button>' +
      '</div>' +
      '<p class="footer-note">Tus publicaciones se guardan en este dispositivo.</p>';
  }

  function howView() {
    return backButton('Bazar', 'feed') +
      '<p class="kicker">Cómo funciona</p>' +
      '<h1 style="font-size:21px;margin-top:4px">Tiendas con stock y trueque con match</h1>' +
      '<div class="panel" style="margin-top:12px">' +
        '<ol class="steps">' +
          '<li><b>Tiendas oficiales</b>: arriba del bazar. Siempre tienen stock y catálogo propio; el banner abre la tienda.</li>' +
          '<li><b>Catálogo</b>: verás si está actualizado. Si tiene días sin sincronizar, puede haber cambios de precio o falta de stock.</li>' +
          '<li><b>Publica</b> lo que ya no usas y pon tú el precio. El bazar no valora objetos.</li>' +
          '<li><b>Marca «Me interesa»</b> en lo que quieras. Nadie recibe aviso: es privado.</li>' +
          '<li><b>Match</b>: cuando la otra persona también marca algo tuyo, se abre el chat y se comparte el contacto.</li>' +
          '<li><b>Cierra el trato</b> cuando quieras: punto de encuentro y marcar el intercambio.</li>' +
        '</ol>' +
      '</div>' +
      '<div class="panel" style="margin-top:12px">' +
        '<div class="panel-head"><h3>Reportes</h3></div>' +
        '<p style="font-size:12px;color:var(--ink-soft);margin-top:6px">Cualquier artículo o usuario se puede reportar por ' +
        'artículos prohibidos, precio engañoso, falta de stock, actitudes irrespetuosas, spam u otro motivo. ' +
        'El reporte se guarda con fecha y se manda a revisión.</p>' +
      '</div>' +
      '<div class="actions-block">' +
        '<button class="btn primary block" data-act="tab" data-id="publish">Publicar mi primer objeto</button>' +
      '</div>';
  }

  // ---------------------------------------------------------------------------------------------
  // Vistas: tiendas oficiales y catálogo
  // ---------------------------------------------------------------------------------------------

  function storesView() {
    var status = catalogStatus();
    var body = storeList().map(function (store) {
      var list = productsForStore(store.id);
      var available = list.filter(function (product) { return localStock(product) > 0; }).length;
      return '<button class="store-card" data-act="store" data-id="' + store.id + '">' +
        '<span class="store-mark" style="background:' + store.accent + '">' + store.emoji + '</span>' +
        '<span class="store-copy">' +
          '<b>' + esc(store.name) + '</b>' +
          '<small>' + esc(store.tagline) + '</small>' +
          '<em>' + available + ' con stock · ' + esc(store.offer) + '</em>' +
        '</span>' +
        '<span class="go">›</span>' +
      '</button>';
    }).join('');

    return '<p class="kicker">Organizaciones oficiales</p>' +
      '<h1 class="title-md">Tiendas del campus</h1>' +
      '<p class="lede">Venden aquí todo el año. La tienda fija sus productos destacados y el catálogo se ' +
        'sincroniza solo; tú siempre ves si la información está al día.</p>' +
      freshnessChip('block-chip') +
      '<div class="cards">' + body + '</div>' +
      '<div class="panel">' +
        '<div class="panel-head"><h3>Catálogo y actividad</h3><span class="conf">v' + status.head + '</span></div>' +
        '<p class="report-lede">Revisa cuándo cambió un precio, qué se agregó o se retiró, y si tu catálogo ' +
          'está actualizado.</p>' +
        '<button class="btn primary block" data-act="catalogo">Ver catálogo y actividad</button>' +
      '</div>';
  }

  function storeView(id) {
    var store = storeById(id);
    var list = productsForStore(id);
    var pinned = list.filter(function (product) { return store.pinned.indexOf(product.id) >= 0; });
    var rest = list.filter(function (product) { return store.pinned.indexOf(product.id) < 0; });
    var status = catalogStatus();

    var pinnedBlock = pinned.length
      ? '<div class="section-head"><h2>Fijados por la tienda</h2><span>Para impulsar la venta</span></div>' +
        '<div class="product-list">' + pinned.map(function (product) { return productCard(product); }).join('') + '</div>'
      : '';

    return backButton('Tiendas', 'stores') +
      storeBanner(store, { act: 'nothing' }) +
      '<p class="store-lede">' + esc(store.tagline) + '. Desde ' + esc(store.since) + '.</p>' +
      freshnessChip('block-chip') +
      (status.stale
        ? '<div class="note">Este catálogo no está actualizado: el stock y los precios pueden haber cambiado. ' +
          'Actualízalo antes de comprar si algo te urge.</div>'
        : '') +
      pinnedBlock +
      '<div class="section-head"><h2>Últimos agregados</h2><span>' + rest.length + '</span></div>' +
      '<div class="product-list">' + (rest.length
        ? rest.map(function (product) { return productCard(product); }).join('')
        : '<div class="empty"><p>Esta tienda no tiene más productos por ahora.</p></div>') + '</div>';
  }

  function catalogoView() {
    var status = catalogStatus();
    var entries = catalogo.history();
    var activity = catalogo.audit();

    var versions = entries.slice().reverse().map(function (entry) {
      return '<button class="version-row" data-act="version" data-id="' + entry.version + '">' +
        '<span class="v-tag">v' + entry.version + '</span>' +
        '<span class="v-copy">' +
          '<b>' + (entry.kind === 'full' ? 'Catálogo completo' : 'Actualización') + '</b>' +
          '<small>+' + entry.added + ' altas · −' + entry.removed + ' bajas · ' + entry.updated + ' cambios</small>' +
        '</span>' +
        '<span class="go">ver ›</span>' +
      '</button>';
    }).join('');

    var auditList = activity.map(function (entry) {
      var when = entry.at ? ago(entry.at) : 'sin fecha';
      var changes = entry.changes.length
        ? entry.changes.map(function (change) {
            return '<li class="audit-' + change.type + '"><b>' + esc(change.title) + '</b><span>' +
              esc(auditLabel(change)) + '</span></li>';
          }).join('')
        : '<li class="audit-empty"><span>Sin cambios de producto</span></li>';
      return '<div class="audit-block">' +
        '<div class="audit-head"><span class="v-tag">v' + entry.version + '</span>' +
          '<span class="audit-when">' + esc(when) + '</span></div>' +
        '<ul class="audit-list">' + changes + '</ul>' +
      '</div>';
    }).join('');

    return backButton('Tiendas', 'stores') +
      '<p class="kicker">Catálogo oficial</p>' +
      '<h1 class="title-md">Catálogo y actividad</h1>' +
      '<div class="panel ' + (status.stale ? 'panel-stale' : '') + '">' +
        '<div class="panel-head"><h3>Estado</h3><span class="conf">v' + status.head + '</span></div>' +
        '<div class="value-big">' + esc(status.label) + '</div>' +
        '<div class="value-range">Última sincronización: ' + esc(ago(status.syncedAt)) +
          ' · servidor: v' + status.remote + '</div>' +
        (status.stale
          ? '<div class="hint-line stale-line">Mientras no se actualice, un artículo puede estar agotado o ' +
            'haber cambiado de precio. Confirma antes de comprar.</div>'
          : '<div class="hint-line">El catálogo está al día: stock y precios corresponden a la última versión.</div>') +
        '<button class="btn primary block" data-act="sync">' +
          (status.pending ? 'Actualizar catálogo (sólo cambios)' : 'Revisar si hay cambios') + '</button>' +
        (ui.syncResult ? '<div class="sync-result"><b>' +
          (ui.syncResult.kind === 'delta' ? 'Actualizado v' + ui.syncResult.from + ' → v' + ui.syncResult.to :
            (ui.syncResult.kind === 'current' ? 'Sin cambios' : 'Catálogo instalado')) + '</b>' +
          '<span>+' + ui.syncResult.added + ' altas · −' + ui.syncResult.removed + ' bajas · ' +
            ui.syncResult.updated + ' cambios</span>' +
          '<span class="sync-note">' + ui.syncResult.items + ' fichas descargadas' +
            (ui.syncResult.completeCatalogue ? ' (catálogo completo)' : ' (sólo lo que cambió)') + '.</span></div>' : '') +
      '</div>' +

      '<div class="section-head"><h2>Actividad del catálogo</h2><span>' + activity.length + '</span></div>' +
      '<div class="audit">' + (auditList || '<div class="empty"><p>Sin actividad registrada.</p></div>') + '</div>' +
      '<div class="note">Estos registros se guardan en el teléfono: qué producto cambió, de qué precio a cuál, ' +
        'qué se retiró y cuándo. Sirven para reclamar un cobro o reportar un engaño.</div>' +

      '<div class="section-head"><h2>Versiones guardadas</h2><span>' + entries.length + '</span></div>' +
      '<div class="cards">' + versions + '</div>' +
      '<p class="footer-note">El catálogo se descarga completo una sola vez; después sólo llegan los cambios.</p>';
  }

  function versionView(version) {
    var number = Number(version);
    var photo = catalogSnapshot(number);
    if (!photo) return notFound('Esa versión no existe en memoria.');
    var list = photo.products.slice().sort(function (a, b) {
      return (b.createdAt || 0) - (a.createdAt || 0);
    });

    return backButton('Catálogo', 'catalogo') +
      '<p class="kicker">Vista histórica</p>' +
      '<h1 class="title-md">Catálogo v' + number + '</h1>' +
      '<p class="lede">Así estaba el catálogo cuando se publicó esta versión. El actual es v' +
        catalogo.head() + '.</p>' +
      '<div class="section-head"><h2>Productos</h2><span>' + list.length + '</span></div>' +
      '<div class="cards">' + list.map(productRow).join('') + '</div>' +
      '<p class="footer-note">Solo lectura. Para comprar vuelve a la tienda.</p>';
  }

  function notFound(message) {
    return '<div class="empty" style="margin-top:20px"><div class="ico">🧭</div><h3>No encontramos eso</h3>' +
      '<p>' + esc(message || 'Puede que ya no esté publicado.') + '</p>' +
      '<button class="btn primary" data-act="tab" data-id="feed">Volver al bazar</button></div>';
  }

  // ---------------------------------------------------------------------------------------------
  // Pintar
  // ---------------------------------------------------------------------------------------------

  function render() {
    var route = ui.route;
    var html;
    if (route.name === 'feed') html = feedView();
    else if (route.name === 'stores') html = storesView();
    else if (route.name === 'store') html = storeView(route.id);
    else if (route.name === 'catalogo') html = catalogoView();
    else if (route.name === 'version') html = versionView(route.id);
    else if (route.name === 'item') html = itemView(route.id);
    else if (route.name === 'trades') html = tradesView();
    else if (route.name === 'match') html = matchView(route.id);
    else if (route.name === 'profile') html = profileView(route.id);
    else if (route.name === 'publish') html = publishView();
    else if (route.name === 'how') html = howView();
    else html = feedView();

    var main = document.getElementById('main');
    main.innerHTML = html;

    var key = route.name + ':' + (route.id || '');
    if (key !== lastRouteKey) {
      lastRouteKey = key;
      window.scrollTo(0, 0);
    }

    updateTabs();
    if (route.name === 'feed') wireSearch();
    if (route.name === 'publish') wirePublish();
    if (route.name === 'match') wireChat();
    if (route.name === 'profile') wireProfile();
    if (ui.report) wireReport();
  }

  var lastRouteKey = '';

  function updateTabs() {
    var tabOf = {
      feed: 'feed', item: 'feed', how: 'feed',
      stores: 'stores', store: 'stores', catalogo: 'stores', version: 'stores',
      trades: 'trades', match: 'trades',
      publish: 'publish',
      profile: 'me'
    };
    var rootTab = tabOf[ui.route.name] || 'feed';

    var buttons = document.querySelectorAll('.tabbar button');
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].className = buttons[i].getAttribute('data-id') === rootTab ? 'active' : '';
    }

    var badge = document.getElementById('tradesBadge');
    var pending = pendingCount();
    if (badge) {
      badge.textContent = pending > 0 ? String(pending) : '';
      badge.style.display = pending > 0 ? 'grid' : 'none';
    }

    var count = document.getElementById('listCount');
    if (count) {
      var items = filteredListings();
      count.textContent = items.length + (items.length === 1 ? ' objeto' : ' objetos');
    }
  }

  function refreshList() {
    var cards = document.getElementById('cards');
    if (cards) cards.innerHTML = cardsHtml();
    var title = document.getElementById('listTitle');
    if (title) title.textContent = listTitle();
    var count = document.getElementById('listCount');
    if (count) {
      var items = filteredListings();
      count.textContent = items.length + (items.length === 1 ? ' objeto' : ' objetos');
    }
  }

  function wireSearch() {
    var input = document.getElementById('q');
    if (!input) return;
    input.addEventListener('input', function () {
      ui.query = input.value;
      refreshList();
    });
  }

  function wirePublish() {
    var title = document.getElementById('fTitle');
    var desc = document.getElementById('fDesc');
    var asking = document.getElementById('fAsk');
    if (title) title.addEventListener('input', function () { ui.form.title = title.value; });
    if (desc) desc.addEventListener('input', function () { ui.form.description = desc.value; });
    if (asking) asking.addEventListener('input', function () { ui.form.asking = asking.value; });
  }

  function wireChat() {
    var input = document.getElementById('chatInput');
    if (input) input.addEventListener('input', function () { ui.chatDraft = input.value; });
    var cash = document.getElementById('cashInput');
    if (cash) cash.addEventListener('input', function () { ui.cash = cash.value; });
    var log = document.getElementById('chatLog');
    if (log) log.scrollTop = log.scrollHeight;
  }

  function wireProfile() {
    if (!ui.editingProfile) return;
    var name = document.getElementById('pName');
    var phone = document.getElementById('pPhone');
    var town = document.getElementById('pTown');
    if (name) name.addEventListener('input', function () { ui.profileName = name.value; });
    if (phone) phone.addEventListener('input', function () { ui.profilePhone = phone.value; });
    if (town) town.addEventListener('input', function () { ui.profileTown = town.value; });
  }

  function wireReport() {
    var note = document.getElementById('reportNote');
    if (note) note.addEventListener('input', function () { if (ui.report) ui.report.note = note.value; });
  }

  // ---------------------------------------------------------------------------------------------
  // Acciones
  // ---------------------------------------------------------------------------------------------

  function navigate(route) {
    if (ui.route) history.push(ui.route);
    ui.route = route;
    ui.report = null;
    ui.confirmRetire = null;
    render();
  }

  function goBack(fallback) {
    var previous = history.pop();
    if (previous) {
      ui.route = previous;
      render();
      return;
    }
    ui.route = fallback || { name: 'feed' };
    render();
  }

  function goTab(tab) {
    history.length = 0;
    ui.report = null;
    if (tab === 'feed') ui.route = { name: 'feed' };
    else if (tab === 'stores') ui.route = { name: 'stores' };
    else if (tab === 'trades') ui.route = { name: 'trades' };
    else if (tab === 'publish') ui.route = { name: 'publish' };
    else if (tab === 'me') { ui.editingProfile = false; ui.route = { name: 'profile', id: data.me }; }
    render();
  }

  function myBarterables() {
    return data.listings.filter(function (item) {
      return item.ownerId === data.me && item.barter && item.status === 'DISPONIBLE';
    });
  }

  function toggleInterest(listingId) {
    var item = listing(listingId);
    if (!item) { flash('Ese objeto ya no está disponible'); return; }
    if (item.ownerId === data.me) { flash('Es tu propia publicación'); return; }
    if (!item.barter) { flash('Ese objeto es sólo venta'); return; }
    if (item.status !== 'DISPONIBLE') { flash('Ese objeto ya está apartado'); return; }

    var existing = interestMark(listingId, data.me);
    if (existing) {
      if (matchWith(item.ownerId)) {
        flash('Ya hay chat abierto: retira el interés desde el trueque');
        return;
      }
      data.interests = data.interests.filter(function (mark) { return mark !== existing; });
      save();
      flash('Interés retirado. Nadie se enteró.');
      render();
      return;
    }

    data.interests.push({
      id: 'i_' + Date.now().toString(36),
      listingId: listingId,
      fromUserId: data.me,
      createdAt: Date.now()
    });
    save();
    var created = reconcileMatches();
    if (created.length) {
      flash('¡Match con ' + created[0].other.name + '! Ya pueden hablar');
      try { if (window.Nahua && Nahua.haptic) Nahua.haptic('confirm'); } catch (error) { /* sin host */ }
    } else {
      flash('Te interesa. Es privado: sólo se avisa si hay match.');
    }
    render();
  }

  function openMatchWith(otherId) {
    var item = matchWith(otherId);
    if (!item) { flash('Todavía no hay match con esa persona'); return; }
    navigate({ name: 'match', id: item.id });
  }

  function openMatch(id) {
    var item = match(id);
    if (!item) return;
    item.lastReadByMe = Date.now();
    save();
    ui.chatDraft = '';
    ui.cash = '';
    navigate({ name: 'match', id: id });
  }

  function sendMessage(id) {
    var item = match(id);
    if (!item || item.status !== 'CHAT') return;
    var text = (ui.chatDraft || '').trim();
    if (!text) { flash('Escribe algo primero'); return; }
    item.messages.push({
      id: 'g_' + Date.now().toString(36),
      fromUserId: data.me,
      kind: 'text',
      at: Date.now(),
      text: text.slice(0, 240)
    });
    item.lastReadByMe = Date.now();
    save();
    ui.chatDraft = '';
    render();
  }

  function agreeTrade(id) {
    var item = match(id);
    if (!item || item.status !== 'CHAT') return;
    var pair = selectedPair(item);
    if (!pair.give || !pair.get) { flash('Falta un objeto de cada lado para cerrar el trato'); return; }
    var select = document.getElementById('meetingSelect');
    var meeting = select ? select.value : MEETING_POINTS[0];

    item.status = 'ACORDADO';
    item.meeting = meeting;
    item.cashCents = pesosToCents(ui.cash);
    item.giveIds = [pair.give.id];
    item.getIds = [pair.get.id];
    pair.give.status = 'RESERVADO';
    pair.get.status = 'RESERVADO';
    item.messages.push({
      id: 'g_' + Date.now().toString(36),
      fromUserId: 'system',
      kind: 'system',
      at: Date.now(),
      text: 'Trato acordado: ' + pair.give.title + ' (' + money(pair.give.askingCents) + ') ⇄ ' +
        pair.get.title + ' (' + money(pair.get.askingCents) + ') en ' + meeting +
        (item.cashCents > 0 ? ', con ' + money(item.cashCents) + ' en efectivo' : '') + '.'
    });
    save();
    announce('Trato acordado con ' + user(otherOf(item)).name,
      pair.give.title + ' ⇄ ' + pair.get.title + '. Punto de encuentro: ' + meeting + '.');
    flash('Trato acordado. Acuerden día y hora en el chat');
    ui.cash = '';
    render();
  }

  function completeTrade(id) {
    var item = match(id);
    if (!item || item.status !== 'ACORDADO') return;
    var pair = selectedPair(item);
    var give = item.giveIds && item.giveIds.length ? listing(item.giveIds[0]) : pair.give;
    var get = item.getIds && item.getIds.length ? listing(item.getIds[0]) : pair.get;
    if (give) give.status = 'TRUEQUEADO';
    if (get) get.status = 'TRUEQUEADO';

    item.status = 'CERRADO';
    item.messages.push({
      id: 'g_' + Date.now().toString(36),
      fromUserId: 'system',
      kind: 'system',
      at: Date.now(),
      text: 'Trueque marcado como intercambiado' + (item.meeting ? ' en ' + item.meeting : '') + '.'
    });
    if (data.users[data.me]) data.users[data.me].trades += 1;
    var otherId = otherOf(item);
    if (data.users[otherId]) data.users[otherId].trades += 1;
    save();
    announce('Trueque completado con ' + user(otherId).name, 'El intercambio quedó registrado en el bazar.');
    flash('Trueque completado. ¡Gracias por mover la comunidad!');
    render();
  }

  function archiveTrade(id) {
    var item = match(id);
    if (!item) return;
    item.status = 'ARCHIVADO';
    var pair = selectedPair(item);
    if (pair.give && pair.give.status === 'RESERVADO') pair.give.status = 'DISPONIBLE';
    if (pair.get && pair.get.status === 'RESERVADO') pair.get.status = 'DISPONIBLE';

    var otherId = otherOf(item);
    data.interests = data.interests.filter(function (mark) {
      if (mark.fromUserId !== data.me) return true;
      var target = listing(mark.listingId);
      return !(target && target.ownerId === otherId);
    });
    item.messages.push({
      id: 'g_' + Date.now().toString(36),
      fromUserId: 'system',
      kind: 'system',
      at: Date.now(),
      text: 'Retiraste tu interés. El chat queda como historial y los objetos vuelven al bazar.'
    });
    save();
    flash('Interés retirado. Los objetos vuelven al bazar');
    render();
  }

  function copyPhone(userId) {
    var profile = user(userId);
    var phone = formatPhone(profile.phone);
    if (!phone) { flash('Ese perfil no tiene teléfono'); return; }
    try { if (window.Nahua && Nahua.copy) Nahua.copy(phone); } catch (error) { /* sin portapapeles */ }
    flash('Teléfono copiado: ' + phone);
  }

  function saveProfile() {
    var profile = me();
    var name = (ui.profileName != null ? ui.profileName : profile.name).trim();
    var phone = phoneDigits(ui.profilePhone != null ? ui.profilePhone : profile.phone);
    var town = (ui.profileTown != null ? ui.profileTown : profile.town).trim();

    if (name.length < 4) { flash('Escribe tu nombre completo'); return; }
    if (phone.length !== 10) { flash('El teléfono debe tener 10 dígitos'); return; }
    if (!town) { flash('Escribe tu comunidad o municipio'); return; }

    profile.name = name.slice(0, 40);
    profile.phone = phone;
    profile.town = town.slice(0, 40);
    data.users[data.me] = profile;
    save();
    ui.editingProfile = false;
    ui.profileName = null;
    ui.profilePhone = null;
    ui.profileTown = null;
    flash('Datos guardados. Tu teléfono sólo se comparte con match');
    render();
  }

  function openReport(type, id) {
    ui.report = { type: type, id: id, reason: null, note: '' };
    render();
  }

  function sendReport() {
    var report = ui.report;
    if (!report) return;
    if (!report.reason) { flash('Elige el motivo del reporte'); return; }

    var reason = REPORT_REASONS.filter(function (entry) { return entry.id === report.reason; })[0];
    var targetTitle = '';
    if (report.type === 'listing') {
      var item = listing(report.id);
      if (!item) { flash('Ese artículo ya no existe'); return; }
      targetTitle = item.title;
      item.reportedByMe = true;
    } else {
      targetTitle = user(report.id).name;
    }

    var entry = {
      id: 'rep_' + Date.now().toString(36),
      targetType: report.type,
      targetId: report.id,
      targetTitle: targetTitle,
      reason: report.reason,
      reasonLabel: reason ? reason.label : report.reason,
      note: (report.note || '').trim().slice(0, 240),
      at: Date.now(),
      byUserId: data.me
    };
    data.reports.push(entry);
    save();

    // El reporte también va al servidor (dedupe de 24 h allá); si falla, queda el registro local.
    if (serverAvailable() && typeof Nahua.catalogReport === 'function') {
      Nahua.catalogReport({
        reportId: entry.id,
        targetType: entry.targetType,
        targetId: entry.targetId,
        targetTitle: entry.targetTitle,
        reason: entry.reason,
        reasonLabel: entry.reasonLabel,
        note: entry.note,
        reporter: { userId: me().id, phone: me().phone },
        createdAt: entry.at
      }).catch(function () { /* sin red: el reporte ya está guardado en el teléfono */ });
    }

    announce('Reporte registrado: ' + targetTitle,
      (reason ? reason.label : 'Reporte') + '. Queda guardado con fecha y se manda a revisión.');
    flash('Reporte enviado. Gracias por cuidar el bazar');
    ui.report = null;
    render();
  }

  /**
   * Registra la compra en el servidor: allá se valida el precio y se descuenta el stock de forma
   * atómica. La cartera es local, así que el pago ya ocurrió; si el servidor lo rechaza se avisa y
   * se vuelve a pedir el catálogo para no seguir mostrando stock que ya no existe.
   */
  function registerOrder(product) {
    if (!serverAvailable() || catalogo.origin() !== 'remote') return;
    if (typeof Nahua.catalogOrder !== 'function') return;

    var person = me();
    Nahua.catalogOrder({
      orderId: 'ord_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
      storeId: product.storeId,
      productId: product.id,
      quantity: 1,
      unitPriceCents: product.priceCents,
      catalogVersion: catalogo.head(),
      buyer: { userId: person.id, phone: person.phone }
    }).then(function (response) {
      if (response && response.status === 'ok') {
        flash('Servidor: compra confirmada · quedan ' + response.stockLeft);
        render();
      }
    }).catch(function (error) {
      var code = (error && (error.code || error.message)) || '';
      flash(code === 'out_of_stock'
        ? 'El servidor dice que ya no hay stock: se actualiza el catálogo'
        : (code === 'price_changed'
          ? 'El servidor cambió el precio: se actualiza el catálogo'
          : 'El servidor no confirmó la compra'));
      pullCatalog().then(render);
    });
  }

  function buyCommunity(id) {
    var item = listing(id);
    if (!item) return;
    if (!window.Nahua || typeof Nahua.pay !== 'function') {
      flash('La compra necesita la app Nahua');
      return;
    }
    var owner = user(item.ownerId);
    Nahua.pay({
      amount: item.askingCents / 100,
      concept: item.title,
      merchant: owner.name
    }).then(function () {
      item.status = 'VENDIDO';
      save();
      flash('Pagaste ' + money(item.askingCents) + ' a ' + owner.name);
      navigate({ name: 'feed' });
    }).catch(function (error) {
      var messages = {
        insufficient_funds: 'Saldo insuficiente. Recarga tu cartera.',
        cancelled: 'Pago cancelado.',
        invalid_amount: 'Monto inválido.'
      };
      flash((error && messages[error.error]) || 'No pudimos completar el pago.');
    });
  }

  function buyOfficial(id) {
    var product = officialProduct(id);
    if (!product) { flash('Ese producto ya no está en el catálogo'); return; }
    var stock = localStock(product);
    if (stock <= 0) { flash('Ese producto está agotado'); return; }

    var status = catalogStatus();
    if (status.stale && ui.confirmBuy !== id) {
      ui.confirmBuy = id;
      flash('Catálogo sin actualizar: revisa antes de pagar. Toca otra vez para confirmar');
      render();
      return;
    }

    if (!window.Nahua || typeof Nahua.pay !== 'function') {
      flash('La compra necesita la app Nahua');
      return;
    }

    var store = storeById(product.storeId);
    ui.confirmBuy = null;
    Nahua.pay({
      amount: product.priceCents / 100,
      concept: product.title,
      merchant: store.name
    }).then(function () {
      purchases[product.id] = Number(purchases[product.id] || 0) + 1;
      savePurchases();
      flash('Pagaste ' + money(product.priceCents) + ' a ' + store.short + ' · stock ' + localStock(product));
      render();
      registerOrder(product);
    }).catch(function (error) {
      var messages = {
        insufficient_funds: 'Saldo insuficiente. Recarga tu cartera.',
        cancelled: 'Pago cancelado.',
        invalid_amount: 'Monto inválido.'
      };
      flash((error && messages[error.error]) || 'No pudimos completar el pago.');
    });
  }

  function publishListing() {
    var form = ui.form;
    var title = (form.title || '').trim();
    if (title.length < 4) { flash('Ponle un nombre de al menos 4 letras a tu objeto'); return; }
    var asking = pesosToCents(form.asking);
    if (asking <= 0) { flash('Escribe el precio que pides por tu objeto'); return; }

    var item = {
      id: 'L_' + Date.now().toString(36),
      ownerId: data.me,
      title: title.slice(0, 60),
      description: (form.description || '').trim().slice(0, 240),
      emoji: form.emoji,
      category: form.category,
      condition: form.condition,
      askingCents: asking,
      barter: !!form.barter,
      status: 'DISPONIBLE',
      createdAt: Date.now()
    };

    data.listings.unshift(item);
    save();
    flash(item.barter ? 'Publicado con trueque: el chat se abre sólo con match' : 'Publicado en el bazar');
    try { if (window.Nahua && Nahua.toast) Nahua.toast('Tu objeto ya está en el bazar'); } catch (error) { /* sin host */ }
    ui.form = freshForm();
    history = [{ name: 'feed' }];
    ui.route = { name: 'item', id: item.id };
    render();
  }

  function retireListing(id) {
    var item = listing(id);
    if (!item || item.ownerId !== data.me) return;
    item.status = 'OCULTO';
    save();
    flash('Retiraste el objeto del bazar');
    history = [{ name: 'feed' }];
    ui.route = { name: 'profile', id: data.me };
    render();
  }

  function runSync() {
    if (!serverAvailable()) {
      var local = catalogo.sync();
      ui.syncResult = local;
      flash(local.kind === 'delta'
        ? 'Sin servidor: catálogo de demostración v' + local.from + ' → v' + local.to
        : 'Sin servidor: ya tienes el catálogo de demostración');
      render();
      return;
    }

    flash('Consultando ' + SERVER_BASE.replace('https://', '') + '…');
    pullCatalog().then(function (result) {
      ui.syncResult = result;
      if (!result) {
        flash('No pudimos conectar con el servidor');
      } else if (result.kind === 'delta') {
        flash('Catálogo v' + result.from + ' → v' + result.to + ': ' + result.items + ' fichas');
      } else if (result.kind === 'current') {
        flash('Catálogo al día · v' + result.to);
      } else {
        flash('Catálogo del servidor instalado · v' + result.to);
      }
      render();
    });
  }

  function onAction(action, id) {
    if (action === 'tab') return goTab(id);
    if (action === 'back') {
      if (id === 'trades') return goBack({ name: 'trades' });
      if (id === 'stores') return goBack({ name: 'stores' });
      if (id === 'catalogo') return goBack({ name: 'catalogo' });
      if (id === 'item') return goBack({ name: 'item', id: ui.route.id });
      return goBack({ name: 'feed' });
    }
    if (action === 'open') return navigate({ name: 'item', id: id });
    if (action === 'profile') return navigate({ name: 'profile', id: id });
    if (action === 'store') return navigate({ name: 'store', id: id });
    if (action === 'catalogo') return navigate({ name: 'catalogo' });
    if (action === 'version') return navigate({ name: 'version', id: id });
    if (action === 'sync') return runSync();
    if (action === 'interest') return toggleInterest(id);
    if (action === 'open-match') return openMatchWith(id);
    if (action === 'match') return openMatch(id);
    if (action === 'pick-give') { ui.giveId = id; render(); return; }
    if (action === 'pick-get') { ui.getId = id; render(); return; }
    if (action === 'send-msg') return sendMessage(id);
    if (action === 'agree') return agreeTrade(id);
    if (action === 'complete') return completeTrade(id);
    if (action === 'archive') return archiveTrade(id);
    if (action === 'copy-phone') return copyPhone(id);
    if (action === 'buy') return buyCommunity(id);
    if (action === 'buy-official') return buyOfficial(id);
    if (action === 'report-open') return openReport('listing', id);
    if (action === 'report-user') return openReport('user', id);
    if (action === 'report-reason') {
      if (ui.report) ui.report.reason = id;
      render();
      return;
    }
    if (action === 'report-send') return sendReport();
    if (action === 'report-cancel') { ui.report = null; render(); return; }
    if (action === 'filter') { ui.filter = id; render(); return; }
    if (action === 'trades-tab') { ui.tradesTab = id; render(); return; }
    if (action === 'how') return navigate({ name: 'how' });
    if (action === 'publish') return publishListing();
    if (action === 'edit-profile') {
      var profile = me();
      if (!ui.editingProfile) {
        ui.profileName = profile.name;
        ui.profilePhone = formatPhone(profile.phone);
        ui.profileTown = profile.town;
      }
      ui.editingProfile = !ui.editingProfile;
      render();
      return;
    }
    if (action === 'save-profile') return saveProfile();
    if (action === 'retire') { ui.confirmRetire = id; render(); return; }
    if (action === 'retire-confirm') { ui.confirmRetire = null; return retireListing(id); }
    if (action === 'retire-cancel') { ui.confirmRetire = null; render(); return; }
    if (action === 'set-emoji') { ui.form.emoji = id; render(); return; }
    if (action === 'set-cat') {
      ui.form.category = id;
      ui.form.emoji = categoryById(id).emoji;
      render();
      return;
    }
    if (action === 'set-cond') { ui.form.condition = id; render(); return; }
    if (action === 'toggle-barter') { ui.form.barter = !ui.form.barter; render(); return; }
    if (action === 'close') { if (window.Nahua && Nahua.close) Nahua.close(); }
  }

  document.addEventListener('click', function (event) {
    var node = event.target;
    while (node && node !== document) {
      if (node.getAttribute && node.getAttribute('data-act')) {
        var action = node.getAttribute('data-act');
        var id = node.getAttribute('data-id');
        event.preventDefault();
        onAction(action, id);
        return;
      }
      node = node.parentNode;
    }
  });

  document.addEventListener('gesturestart', function (event) { event.preventDefault(); });

  // ---------------------------------------------------------------------------------------------
  // Arranque
  // ---------------------------------------------------------------------------------------------

  function start() {
    try {
      var session = window.Nahua && Nahua.user ? Nahua.user() : null;
      if (session && session.name) {
        var mine = me();
        mine.name = session.name;
        if (session.phone) {
          var digits = phoneDigits(session.phone);
          if (digits.length === 10) mine.phone = digits;
        }
      }
    } catch (error) {
      // sin sesión: se usa el perfil de la comunidad
    }

    reconcileMatches();
    render();
    // El catálogo real vive en el servidor del teléfono: se pide al abrir, sin bloquear la UI.
    pullCatalog().then(function (result) {
      if (result) render();
    });
  }

  // Expuesto a propósito: el candado de pruebas del bundle verifica las reglas.
  window.Bazar = {
    rule: 'chat-only-on-mutual-interest',
    data: function () { return data; },
    catalogo: catalogo,
    source: SOURCE,
    stores: function () { return storeList(); },
    pullCatalog: pullCatalog,
    server: function () { return { state: ui.server, base: SERVER_BASE, available: serverAvailable() }; },
    catalogStatus: catalogStatus,
    audit: function () { return catalogo.audit(); },
    localStock: localStock,
    mutual: function (otherId) { return isMutual(otherId); },
    pending: function () { return pendingCount(); },
    formatPhone: formatPhone
  };

  start();
})();
