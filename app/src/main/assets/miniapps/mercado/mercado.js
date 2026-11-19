/*
 * Mercado oficial — landing de tiendas del campus
 * ---------------------------------------------------------------------------------------------
 * Portada con banner de las tiendas oficiales (imagen/arte con sus últimos productos y ofertas),
 * los últimos productos de todo el mercado y el acceso a cada tienda, que ordena sus productos
 * empezando por los que ella misma fijó para impulsar la venta y luego por los más recientes.
 *
 * El catálogo no se descarga completo cada vez: `catalogo.js` guarda la foto inicial y aplica
 * parches con sólo lo agregado, lo retirado y lo cambiado. Todas las versiones quedan en memoria
 * y se pueden consultar (pestaña Catálogo).
 */
(function () {
  'use strict';

  var STORE_KEY = 'nahua.mercado.compras.v1';

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
      pinned: ['a1']
    }
  ];

  function item(id, storeId, title, priceCents, emoji, tag, createdAt) {
    return { id: id, storeId: storeId, title: title, priceCents: priceCents, emoji: emoji, tag: tag, createdAt: createdAt };
  }

  // Foto completa: la única descarga que se hace entera.
  var BASE = {
    version: 1,
    stores: STORES,
    products: [
      item('c1', 'coop', 'Taza de cerámica del campus', 6500, '☕', 'Fijado', 100),
      item('c2', 'coop', 'Termo de acero 1 L', 24000, '🧴', 'Más vendido', 101),
      item('c3', 'coop', 'Cuaderno cosido 100 hojas', 4500, '📓', 'Nuevo', 102),
      item('c4', 'coop', 'Mochila ligera para laptop', 38000, '🎒', '', 103),
      item('c5', 'coop', 'Calculadora científica', 22000, '🖩', '', 104),
      item('c6', 'coop', 'Gorra bordada del campus', 15000, '🧢', '', 105),
      item('t1', 'telar', 'Rebozo de telar de cintura', 95000, '🧶', 'Fijado', 106),
      item('t2', 'telar', 'Blusa bordada talla M', 62000, '👚', 'Hecho a mano', 107),
      item('t3', 'telar', 'Morral de hilo tejido', 28000, '👝', '', 108),
      item('t4', 'telar', 'Juego de servilletas bordadas', 21000, '🧵', '', 109),
      item('t5', 'telar', 'Chal de lana de borrego', 54000, '🧣', '', 110),
      item('k1', 'cafe', 'Café en grano de Comala 1 kg', 12000, '☕', 'Fijado', 111),
      item('k2', 'cafe', 'Conserva de tamarindo 500 g', 8500, '🫙', 'De temporada', 112),
      item('k3', 'cafe', 'Miel de abeja 1 L', 16000, '🍯', '', 113),
      item('k4', 'cafe', 'Pan de elote (charola)', 7000, '🌽', 'Horneado hoy', 114),
      item('k5', 'cafe', 'Chocolate de metate 400 g', 11000, '🍫', '', 115),
      item('a1', 'taller', 'Olla de barro grande', 34000, '🏺', 'Fijado', 116),
      item('a2', 'taller', 'Juego de 4 tazas de barro', 26000, '🍵', 'Hecho a mano', 117),
      item('a3', 'taller', 'Canasta de palma tejida', 18000, '🧺', '', 118),
      item('a4', 'taller', 'Molcajete de piedra volcánica', 29000, '🥑', '', 119),
      item('a5', 'taller', 'Figuras de barro pintadas', 9500, '🎨', '', 120),
      item('a6', 'taller', 'Plato decorado con flores', 14000, '🍽️', '', 121)
    ]
  };

  // Sólo lo que cambió, versión por versión. Esto es lo que viaja al sincronizar.
  var PATCHES = [
    {
      version: 2,
      at: 200,
      added: [
        item('c7', 'coop', 'Sudadera del campus', 42000, '🧥', 'Nuevo', 130),
        item('k6', 'cafe', 'Café molido 500 g', 9000, '☕', 'Nuevo', 131)
      ],
      removed: ['c5'],
      updated: [{ id: 'k1', priceCents: 13000 }]
    },
    {
      version: 3,
      at: 300,
      added: [
        item('t6', 'telar', 'Cinturón tejido en telar', 19000, '🪢', 'Nuevo', 140),
        item('a7', 'taller', 'Jarra de barro esmaltada', 24000, '🫖', 'Nuevo', 141)
      ],
      removed: ['k4'],
      updated: []
    },
    {
      version: 4,
      at: 400,
      added: [
        item('c8', 'coop', 'Agenda 2027', 12000, '🗓️', 'Nuevo', 150),
        item('a8', 'taller', 'Maceta de barro para suculenta', 8000, '🪴', 'Nuevo', 151)
      ],
      removed: ['c6'],
      updated: [{ id: 't2', priceCents: 58000 }]
    }
  ];

  /**
   * Adaptador del "servidor". En producción esto se cambia por fetch a los endpoints de
   * docs/MERCADO_API.md; la UI y el almacenamiento no cambian.
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
    key: 'nahua.mercado.catalogo.v1'
  });

  if (!catalogo.installed()) {
    catalogo.install();
  }

  function loadPurchases() {
    try {
      var raw = window.localStorage.getItem(STORE_KEY);
      return raw ? JSON.parse(raw) : {};
    } catch (error) {
      return {};
    }
  }

  var purchases = loadPurchases();

  var ui = {
    route: { name: 'home' },
    storeId: null,
    version: null,
    syncResult: null,
    query: '',
    tab: 'home'
  };

  var history = [];

  // ---------------------------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------------------------

  function esc(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function money(cents) {
    var value = Math.round(Number(cents) || 0);
    var units = Math.floor(Math.abs(value) / 100);
    var decimals = Math.abs(value) % 100;
    var text = units.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    if (decimals > 0) text += '.' + (decimals < 10 ? '0' + decimals : String(decimals));
    return '$' + text;
  }

  function storeById(id) {
    for (var i = 0; i < STORES.length; i++) if (STORES[i].id === id) return STORES[i];
    return STORES[0];
  }

  function snapshotOf(version) {
    return catalogo.snapshot(version);
  }

  function productsOf(version) {
    var photo = snapshotOf(version);
    return photo ? photo.products : [];
  }

  function productsForStore(storeId, version) {
    var store = storeById(storeId);
    var list = productsOf(version).filter(function (product) { return product.storeId === storeId; });
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

  function latestProducts(limit, version) {
    return productsOf(version).slice().sort(function (a, b) {
      return (b.createdAt || 0) - (a.createdAt || 0);
    }).slice(0, limit);
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

  function backButton(label, route) {
    return '<button class="back" data-act="back" data-id="' + esc(route || '') + '">← ' + esc(label) + '</button>';
  }

  function moneyTag(cent) {
    return '<span class="price">' + money(cent) + '</span>';
  }

  function storeBanner(store, options) {
    var settings = options || {};
    var list = productsForStore(store.id, ui.version).slice(0, 3);
    var emojis = list.map(function (product) {
      return '<span class="glyph">' + product.emoji + '</span>';
    }).join('');
    var count = productsForStore(store.id, ui.version).length;

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
    var bought = !!purchases[product.id];
    var tag = product.tag ? '<span class="badge">' + esc(product.tag) + '</span>' : '';

    return '<article class="product-card">' +
      '<div class="product-art" style="background:' + store.accent + '22;border-color:' + store.accent + '55">' +
        '<span class="product-emoji">' + product.emoji + '</span>' +
      '</div>' +
      '<div class="product-body">' +
        '<div class="product-head"><h3>' + esc(product.title) + '</h3>' + tag + '</div>' +
        '<div class="product-store">' + esc(store.short) + '</div>' +
        '<div class="product-foot">' + moneyTag(product.priceCents) +
          (settings.readOnly
            ? '<span class="badge badge-ghost">Vista histórica</span>'
            : (bought
              ? '<span class="badge badge-done">Comprado</span>'
              : '<button class="btn sm primary" data-act="buy" data-id="' + product.id + '">Comprar</button>')) +
        '</div>' +
      '</div>' +
    '</article>';
  }

  function productRow(product) {
    var store = storeById(product.storeId);
    return '<div class="product-row">' +
      '<span class="row-emoji">' + product.emoji + '</span>' +
      '<div class="row-body"><b>' + esc(product.title) + '</b><small>' + esc(store.short) + '</small></div>' +
      '<span class="row-price">' + money(product.priceCents) + '</span>' +
    '</div>';
  }

  // ---------------------------------------------------------------------------------------------
  // Vistas
  // ---------------------------------------------------------------------------------------------

  function homeView() {
    var version = catalogo.head();
    var total = productsOf().length;
    var latest = latestProducts(6);
    var banners = STORES.map(function (store) { return storeBanner(store); }).join('');

    return '<section class="hero">' +
      '<p class="kicker">Mercado del campus</p>' +
      '<h1>Lo último de las <em>tiendas oficiales</em></h1>' +
      '<p class="lede">Ofertas, productos recién llegados y tiendas de la comunidad estudiantil. ' +
        'Toca un banner para entrar a la tienda.</p>' +
      '<div class="hero-stats">' +
        '<div class="stat"><strong>' + STORES.length + '</strong><span>tiendas</span></div>' +
        '<div class="stat"><strong>' + total + '</strong><span>productos</span></div>' +
        '<div class="stat"><strong>v' + version + '</strong><span>catálogo</span></div>' +
      '</div>' +
    '</section>' +

    '<div class="section-head"><h2>Tiendas oficiales</h2><span>Banner y ofertas</span></div>' +
    '<div class="banner-rail">' + banners + '</div>' +

    '<div class="section-head"><h2>Recién llegado</h2><span>' + latest.length + ' de ' + total + '</span></div>' +
    '<div class="product-list">' + latest.map(function (product) { return productCard(product); }).join('') + '</div>' +
    (catalogo.head() < SOURCE.head()
      ? '<button class="update-strip" data-act="catalogo">' +
          '<b>Hay productos nuevos</b><span>Actualiza el catálogo (sólo lo que cambió) →</span></button>'
      : '') +
    '<p class="footer-note">Catálogo v' + version + ' en memoria. Las versiones anteriores se consultan en la pestaña Catálogo.</p>';
  }

  function storesView() {
    var body = STORES.map(function (store) {
      var count = productsForStore(store.id).length;
      return '<button class="store-card" data-act="store" data-id="' + store.id + '">' +
        '<span class="store-mark" style="background:' + store.accent + '">' + store.emoji + '</span>' +
        '<span class="store-copy">' +
          '<b>' + esc(store.name) + '</b>' +
          '<small>' + esc(store.tagline) + '</small>' +
          '<em>' + count + ' productos · ' + esc(store.offer) + '</em>' +
        '</span>' +
        '<span class="go">›</span>' +
      '</button>';
    }).join('');

    return '<p class="kicker">Directorios</p>' +
      '<h1 class="title-md">Tiendas oficiales</h1>' +
      '<p class="lede">Cada tienda ordena sus productos como quiere: primero los que fija para impulsar la venta, ' +
        'luego los últimos que agregó.</p>' +
      '<div class="cards">' + body + '</div>';
  }

  function storeView(id) {
    var store = storeById(id);
    var list = productsForStore(id);
    var pinned = list.filter(function (product) { return store.pinned.indexOf(product.id) >= 0; });
    var rest = list.filter(function (product) { return store.pinned.indexOf(product.id) < 0; });

    var pinnedBlock = pinned.length
      ? '<div class="section-head"><h2>Fijados por la tienda</h2><span>Para impulsar la venta</span></div>' +
        '<div class="product-list">' + pinned.map(function (product) {
          return productCard(product);
        }).join('') + '</div>'
      : '';

    return backButton('Mercado', 'home') +
      storeBanner(store, { act: 'nothing' }) +
      '<p class="store-lede">' + esc(store.tagline) + '. Desde ' + esc(store.since) + '.</p>' +
      pinnedBlock +
      '<div class="section-head"><h2>Últimos agregados</h2><span>' + rest.length + '</span></div>' +
      '<div class="product-list">' + (rest.length
        ? rest.map(function (product) { return productCard(product); }).join('')
        : '<div class="empty"><p>Esta tienda no tiene más productos por ahora.</p></div>') + '</div>';
  }

  function syncSummary(result) {
    if (!result) return '';
    if (result.kind === 'current') {
      return '<div class="sync-result"><b>Estás al día</b><span>No hay cambios desde la versión ' +
        catalogo.head() + '. No se descargó nada.</span></div>';
    }
    var title = result.kind === 'full' ? 'Catálogo instalado' : 'Catálogo actualizado';
    return '<div class="sync-result">' +
      '<b>' + title + ' · v' + result.from + ' → v' + result.to + '</b>' +
      '<span>+' + result.added + ' altas · −' + result.removed + ' bajas · ' +
        result.updated + ' cambios</span>' +
      '<span class="sync-note">' + result.items + ' fichas descargadas' +
        (result.completeCatalogue ? ' (instalación inicial, catálogo completo)' : ' en lugar de volver a bajar las ' +
          catalogo.total() + ' del catálogo') + '.</span>' +
    '</div>';
  }

  function catalogoView() {
    var entries = catalogo.history();
    var pending = catalogo.pending();
    var current = catalogo.total();
    var installed = entries.length ? entries[0] : null;

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

    return '<p class="kicker">Catálogo</p>' +
      '<h1 class="title-md">Catálogo versionado</h1>' +
      '<p class="lede">La foto completa se descarga una sola vez. Después sólo viajan las altas, ' +
        'las bajas y los cambios. Todo el historial se queda en memoria.</p>' +

      '<div class="panel">' +
        '<div class="panel-head"><h3>En memoria</h3><span class="conf">v' + catalogo.head() + '</span></div>' +
        '<div class="value-big">' + current + '</div>' +
        '<div class="value-range">productos en el catálogo actual</div>' +
        (installed ? '<div class="hint-line">Descarga completa: v' + installed.version + ' · ' +
          installed.added + ' fichas. Desde entonces sólo deltas.</div>' : '') +
        (pending.items > 0
          ? '<div class="pending-line">Hay una actualización: v' + pending.from + ' → v' + pending.to +
            ' (' + pending.items + ' fichas, no el catálogo entero).</div>' +
            '<button class="btn primary block" data-act="sync">Buscar actualización</button>'
          : '<div class="pending-line">Sin cambios pendientes en el servidor.</div>' +
            '<button class="btn block" data-act="sync">Revisar de nuevo</button>') +
        syncSummary(ui.syncResult) +
      '</div>' +

      '<div class="section-head"><h2>Versiones guardadas</h2><span>' + entries.length + '</span></div>' +
      '<div class="cards">' + versions + '</div>' +
      '<div class="note">Consultar una versión vieja no cambia el catálogo actual ni descarga nada: ' +
        'se reconstruye con la foto inicial y los parches que ya están en memoria.</div>' +
      '<p class="footer-note">API: GET /catalog?full=1 · GET /catalog/delta?since=N · GET /catalog/head</p>';
  }

  function versionView(version) {
    var number = Number(version);
    var photo = snapshotOf(number);
    if (!photo) return notFound('Esa versión no existe en memoria.');
    var list = photo.products.slice().sort(function (a, b) {
      return (b.createdAt || 0) - (a.createdAt || 0);
    });
    var missing = catalogo.total() - list.length;

    return backButton('Catálogo', 'catalogo') +
      '<p class="kicker">Vista histórica</p>' +
      '<h1 class="title-md">Catálogo v' + number + '</h1>' +
      '<p class="lede">Así estaba el catálogo cuando se publicó esta versión. El actual es v' +
        catalogo.head() + '.</p>' +
      '<div class="banner-rail">' + STORES.map(function (store) {
        var count = list.filter(function (product) { return product.storeId === store.id; }).length;
        return '<div class="mini-store" style="--accent:' + store.accent + '">' +
          '<span>' + store.emoji + '</span><b>' + esc(store.short) + '</b><small>' + count + '</small></div>';
      }).join('') + '</div>' +
      '<div class="section-head"><h2>Productos</h2><span>' + list.length + '</span></div>' +
      '<div class="cards">' + list.map(productRow).join('') + '</div>' +
      (missing > 0
        ? '<div class="note">Esta versión tiene ' + Math.abs(missing) + ' productos ' +
            (missing > 0 ? 'menos' : 'más') + ' que el catálogo actual: los cambios posteriores no se aplican aquí.</div>'
        : '') +
      '<p class="footer-note">Solo lectura. Para comprar vuelve al catálogo actual.</p>';
  }

  function notFound(message) {
    return '<div class="empty"><div class="ico">🧭</div><h3>No encontramos eso</h3>' +
      '<p>' + esc(message) + '</p>' +
      '<button class="btn primary" data-act="tab" data-id="home">Volver al mercado</button></div>';
  }

  // ---------------------------------------------------------------------------------------------
  // Pintar
  // ---------------------------------------------------------------------------------------------

  function render() {
    var route = ui.route;
    var html;
    if (route.name === 'home') html = homeView();
    else if (route.name === 'stores') html = storesView();
    else if (route.name === 'store') html = storeView(route.id);
    else if (route.name === 'catalogo') html = catalogoView();
    else if (route.name === 'version') html = versionView(route.id);
    else html = homeView();

    var main = document.getElementById('main');
    main.innerHTML = html;
    window.scrollTo(0, 0);

    var headLabel = document.getElementById('headVersion');
    if (headLabel) headLabel.textContent = String(catalogo.head());

    var tabOf = {
      home: 'home', stores: 'stores', store: 'stores',
      catalogo: 'catalogo', version: 'catalogo'
    };
    var rootTab = tabOf[route.name] || 'home';
    var buttons = document.querySelectorAll('.tabbar button');
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].className = buttons[i].getAttribute('data-id') === rootTab ? 'active' : '';
    }
  }

  function navigate(route) {
    if (ui.route) history.push(ui.route);
    ui.route = route;
    render();
  }

  function goBack(fallback) {
    var previous = history.pop();
    if (previous) {
      ui.route = previous;
      render();
      return;
    }
    ui.route = fallback || { name: 'home' };
    render();
  }

  function goTab(tab) {
    history.length = 0;
    if (tab === 'home') ui.route = { name: 'home' };
    else if (tab === 'stores') ui.route = { name: 'stores' };
    else if (tab === 'catalogo') ui.route = { name: 'catalogo' };
    render();
  }

  function buy(productId) {
    var product = null;
    productsOf().forEach(function (entry) { if (entry.id === productId) product = entry; });
    if (!product) { flash('Ese producto ya no está en el catálogo'); return; }
    if (!window.Nahua || typeof Nahua.pay !== 'function') {
      flash('La compra necesita la app Nahua');
      return;
    }
    var store = storeById(product.storeId);
    Nahua.pay({
      amount: product.priceCents / 100,
      concept: product.title,
      merchant: store.name
    }).then(function () {
      purchases[product.id] = Date.now();
      try { window.localStorage.setItem(STORE_KEY, JSON.stringify(purchases)); } catch (error) { /* sin storage */ }
      flash('Pagaste ' + money(product.priceCents) + ' a ' + store.short);
      render();
    }).catch(function (error) {
      var messages = {
        insufficient_funds: 'Saldo insuficiente. Recarga tu cartera.',
        cancelled: 'Pago cancelado.',
        invalid_amount: 'Monto inválido.'
      };
      flash((error && messages[error.error]) || 'No pudimos completar el pago.');
    });
  }

  function runSync() {
    var result = catalogo.sync();
    ui.syncResult = result;
    if (result.kind === 'delta') {
      flash('Catálogo v' + result.from + ' → v' + result.to + ': ' + result.items + ' fichas');
    } else if (result.kind === 'current') {
      flash('Ya tienes la última versión');
    } else {
      flash('Catálogo instalado desde cero');
    }
    render();
  }

  document.addEventListener('click', function (event) {
    var node = event.target;
    while (node && node !== document) {
      if (node.getAttribute && node.getAttribute('data-act')) {
        var action = node.getAttribute('data-act');
        var id = node.getAttribute('data-id');
        event.preventDefault();
        if (action === 'tab') return goTab(id);
        if (action === 'back') return goBack({ name: 'home' });
        if (action === 'store') return navigate({ name: 'store', id: id });
        if (action === 'nothing') return;
        if (action === 'catalogo') return navigate({ name: 'catalogo' });
        if (action === 'version') return navigate({ name: 'version', id: id });
        if (action === 'sync') return runSync();
        if (action === 'buy') return buy(id);
        return;
      }
      node = node.parentNode;
    }
  });

  document.addEventListener('gesturestart', function (event) { event.preventDefault(); });

  // El andamio de pruebas lee el catálogo real, no una copia de la UI.
  window.Mercado = {
    catalogo: catalogo,
    source: SOURCE,
    snapshot: function (version) { return catalogo.snapshot(version); },
    stores: function () { return STORES; },
    productsForStore: function (id, version) { return productsForStore(id, version); }
  };

  render();
})();
