/*
 * Bazar/Trueque comunitario — lógica de la mini app
 * ---------------------------------------------------------------------------------------------
 * Cualquiera de la comunidad publica lo que ya no usa. Cada objeto se valora (precio de referencia,
 * estado, uso y accesorios) para saber cuánto vale de verdad; sólo si quien publica habilita la
 * opción, el objeto entra al trueque. Cuando alguien pulsa "Truquear" se avisa al dueño, que puede
 * ver el perfil del interesado con los objetos que ofrece y cerrar el trato — con compensación en
 * efectivo si la diferencia de valor lo pide.
 *
 * La valoración la calcula el anfitrión (BarterValuator.kt) a través del puente Nahua.valuate;
 * valuateLocal() es el espejo para cuando la página corre fuera de la app (navegador de escritorio)
 * y debe mantenerse sincronizado con esas constantes.
 */
(function () {
  'use strict';

  // ---------------------------------------------------------------------------------------------
  // Catálogo de valoración (espejo de BarterValuator.kt — mismo orden de factores y constantes)
  // ---------------------------------------------------------------------------------------------

  var CATEGORIES = [
    { id: 'PHONES', label: 'Celulares', emoji: '📱', resale: 0.58, yearlyLoss: 0.34, floor: 0.28, demand: 1.04 },
    { id: 'COMPUTERS', label: 'Computadoras', emoji: '💻', resale: 0.60, yearlyLoss: 0.32, floor: 0.30, demand: 1.02 },
    { id: 'ELECTRONICS', label: 'Electrónica', emoji: '🎧', resale: 0.62, yearlyLoss: 0.30, floor: 0.30, demand: 1.00 },
    { id: 'APPLIANCES', label: 'Línea blanca', emoji: '🧺', resale: 0.55, yearlyLoss: 0.22, floor: 0.32, demand: 1.02 },
    { id: 'FURNITURE', label: 'Muebles', emoji: '🪑', resale: 0.50, yearlyLoss: 0.12, floor: 0.34, demand: 1.06 },
    { id: 'TOOLS', label: 'Herramientas', emoji: '🔧', resale: 0.60, yearlyLoss: 0.10, floor: 0.40, demand: 1.08 },
    { id: 'BIKES', label: 'Bicis', emoji: '🚲', resale: 0.55, yearlyLoss: 0.12, floor: 0.34, demand: 1.06 },
    { id: 'SPORTS', label: 'Deportes', emoji: '⚽', resale: 0.50, yearlyLoss: 0.12, floor: 0.32, demand: 1.00 },
    { id: 'CLOTHING', label: 'Ropa', emoji: '👕', resale: 0.40, yearlyLoss: 0.20, floor: 0.25, demand: 0.96 },
    { id: 'BOOKS', label: 'Libros', emoji: '📚', resale: 0.36, yearlyLoss: 0.08, floor: 0.28, demand: 0.98 },
    { id: 'TOYS', label: 'Juguetes', emoji: '🧸', resale: 0.45, yearlyLoss: 0.14, floor: 0.28, demand: 0.98 },
    { id: 'MUSIC', label: 'Instrumentos', emoji: '🎸', resale: 0.58, yearlyLoss: 0.09, floor: 0.34, demand: 1.02 },
    { id: 'COLLECTIBLES', label: 'Coleccionables', emoji: '🪙', resale: 0.78, yearlyLoss: 0.03, floor: 0.45, demand: 1.10 },
    { id: 'OTHER', label: 'Otro', emoji: '📦', resale: 0.50, yearlyLoss: 0.16, floor: 0.30, demand: 1.00 }
  ];

  var CONDITIONS = [
    { id: 'NEW', label: 'Nuevo, sin abrir', short: 'Nuevo', multiplier: 1.28 },
    { id: 'LIKE_NEW', label: 'Como nuevo', short: 'Como nuevo', multiplier: 1.14 },
    { id: 'GOOD', label: 'Buen estado', short: 'Buen estado', multiplier: 1.00 },
    { id: 'WORN', label: 'Usado, con marcas', short: 'Usado', multiplier: 0.78 },
    { id: 'FOR_PARTS', label: 'Para refacción', short: 'Refacción', multiplier: 0.45 }
  ];

  var STEP_CENTS = 500;
  var MIN_ESTIMATE_CENTS = 2000;
  var MAX_RETAINED = 0.92;
  var RANGE_MARGIN = 0.12;
  var BARTER_ACCEPT_RATIO = 0.85;
  var FAIR_BAND = 0.12;
  var MIN_TOP_UP_CENTS = 2000;
  var TOP_UP_STEP_CENTS = 5000;

  var STORE_KEY = 'nahua.bazar.v2';
  var EMOJIS = ['📦', '🚲', '📱', '💻', '🔧', '🪑', '🎸', '🧸', '📚', '👕', '☕', '🎧', '🧺', '🪙', '🎒', '🔊', '🍳', '🚴'];
  var MEETING_POINTS = [
    'Jardín Libertad, Colima centro',
    'Mercado Constitución, Colima',
    'Parque de la Piedra Lisa',
    'Jardín principal de Villa de Álvarez'
  ];
  var AVATAR_COLORS = ['#7E2C44', '#4F7A52', '#3B5BA5', '#B4452F', '#7A4E12', '#4E7C86'];

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

  function roundTo(value, step) {
    if (!step) return Math.round(value);
    return Math.round(value / step) * step;
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

  function ageLabel(months) {
    var m = Math.max(0, Math.round(Number(months) || 0));
    if (m === 0) return 'sin uso';
    if (m < 12) return m === 1 ? '1 mes de uso' : m + ' meses de uso';
    var years = Math.floor(m / 12);
    var rest = m % 12;
    var text = years === 1 ? '1 año de uso' : years + ' años de uso';
    if (rest === 1) text += ' y 1 mes';
    else if (rest > 1) text += ' y ' + rest + ' meses';
    return text;
  }

  // ---------------------------------------------------------------------------------------------
  // Valoración (espejo en JS; el anfitrión manda cuando está disponible)
  // ---------------------------------------------------------------------------------------------

  function inputOf(item) {
    return {
      referenceCents: Math.round(Number(item.referenceCents) || 0),
      category: item.category || 'OTHER',
      condition: item.condition || 'GOOD',
      ageMonths: Math.round(Number(item.ageMonths) || 0),
      complete: item.complete !== false,
      hasReceipt: !!item.hasReceipt
    };
  }

  function percentOf(factor) {
    return Math.round((factor - 1) * 100);
  }

  function valuateLocal(input) {
    var reference = Math.round(Number(input.referenceCents) || 0);
    if (reference <= 0) {
      return {
        status: 'ok', hasValue: false, referenceCents: 0, estimatedCents: 0,
        lowCents: 0, highCents: 0, barterMinCents: 0,
        confidence: 'baja', confidenceLabel: 'baja', factors: []
      };
    }

    var category = categoryById(input.category);
    var condition = conditionById(input.condition);
    var months = Math.max(0, Math.min(360, Math.round(Number(input.ageMonths) || 0)));
    var years = months / 12;
    var ageFactor = Math.max(category.floor, 1 - category.yearlyLoss * years);
    var completeFactor = input.complete === false ? 0.92 : 1;
    var receiptFactor = input.hasReceipt ? 1.04 : 1;
    var marketFactor = category.resale * category.demand;

    var raw = reference * marketFactor * condition.multiplier * ageFactor * completeFactor * receiptFactor;
    var cap = reference * MAX_RETAINED;
    var estimated = Math.max(MIN_ESTIMATE_CENTS, roundTo(Math.min(raw, cap), STEP_CENTS));
    var low = Math.min(estimated, roundTo(estimated * (1 - RANGE_MARGIN), STEP_CENTS));
    var high = Math.max(estimated, roundTo(estimated * (1 + RANGE_MARGIN), STEP_CENTS));
    var barterMin = roundTo(estimated * BARTER_ACCEPT_RATIO, STEP_CENTS);

    var factors = [];
    factors.push({
      label: 'Reventa de ' + category.label.toLowerCase(),
      percent: percentOf(marketFactor),
      tone: marketFactor < 1 ? 'negative' : 'positive'
    });
    if (condition.id !== 'GOOD') {
      factors.push({
        label: 'Estado: ' + condition.label.toLowerCase(),
        percent: percentOf(condition.multiplier),
        tone: condition.multiplier < 1 ? 'negative' : 'positive'
      });
    }
    if (months > 0) {
      factors.push({ label: 'Uso: ' + ageLabel(months).replace(' de uso', ''), percent: percentOf(ageFactor), tone: 'negative' });
    }
    if (input.complete === false) {
      factors.push({ label: 'Sin caja ni todos los accesorios', percent: -8, tone: 'negative' });
    }
    if (input.hasReceipt) {
      factors.push({ label: 'Con factura o garantía', percent: 4, tone: 'positive' });
    }
    if (raw > cap) {
      factors.push({ label: 'Tope de reventa (92 % del precio nuevo)', percent: percentOf(cap / raw), tone: 'neutral' });
    }

    var confidence = 'alta';
    if (category.id === 'OTHER' || reference < 10000) confidence = 'baja';
    else if (category.yearlyLoss >= 0.28 || condition.id === 'FOR_PARTS') confidence = 'media';

    return {
      status: 'ok', hasValue: true, referenceCents: reference,
      estimatedCents: estimated, lowCents: low, highCents: high, barterMinCents: barterMin,
      confidence: confidence, confidenceLabel: confidence, factors: factors
    };
  }

  function compareLocal(mine, theirs) {
    var mineCents = mine.estimatedCents;
    var theirsCents = theirs.estimatedCents;
    if (mineCents <= 0 || theirsCents <= 0) {
      return {
        status: 'ok', mineCents: mineCents, theirsCents: theirsCents, deltaCents: 0,
        ratioPercent: 0, verdict: 'justo', verdictLabel: 'Parejo', cashTopUpCents: 0,
        message: 'Falta valorar uno de los dos objetos: sin número no hay trato.'
      };
    }
    var delta = theirsCents - mineCents;
    var ratio = theirsCents / mineCents;
    var ratioPercent = Math.round((ratio - 1) * 100);

    if (ratio >= 1 + FAIR_BAND) {
      return {
        status: 'ok', mineCents: mineCents, theirsCents: theirsCents, deltaCents: delta,
        ratioPercent: ratioPercent, verdict: 'favorable', verdictLabel: 'A tu favor', cashTopUpCents: 0,
        message: 'Recibes ' + money(delta) + ' más de valor' +
          (Math.abs(ratioPercent) <= 200 ? ' (' + ratioPercent + ' %)' : '') + '. Te conviene truequear.'
      };
    }
    if (ratio <= 1 - FAIR_BAND) {
      var topUp = Math.max(MIN_TOP_UP_CENTS, roundTo(mineCents - theirsCents, TOP_UP_STEP_CENTS));
      return {
        status: 'ok', mineCents: mineCents, theirsCents: theirsCents, deltaCents: delta,
        ratioPercent: ratioPercent, verdict: 'desfavorable', verdictLabel: 'Pide compensación', cashTopUpCents: topUp,
        message: 'Te ofrecen ' + money(mineCents - theirsCents) + ' menos de lo que das. Pide ' +
          money(topUp) + ' en efectivo o un objeto extra.'
      };
    }
    return {
      status: 'ok', mineCents: mineCents, theirsCents: theirsCents, deltaCents: delta,
      ratioPercent: ratioPercent, verdict: 'justo', verdictLabel: 'Parejo', cashTopUpCents: 0,
      message: 'Valores parecidos (' + ratioPercent + ' %): pueden cerrar el trato sin efectivo.'
    };
  }

  var valuationCache = {};

  function signatureOf(item) {
    var input = inputOf(item);
    return [input.referenceCents, input.category, input.condition, input.ageMonths,
      input.complete ? 1 : 0, input.hasReceipt ? 1 : 0].join('|');
  }

  function appraise(item) {
    var signature = signatureOf(item);
    if (valuationCache[signature]) return valuationCache[signature];
    var input = inputOf(item);
    var result = null;
    try {
      if (window.Nahua && typeof Nahua.valuate === 'function') result = Nahua.valuate(input);
    } catch (error) {
      result = null;
    }
    if (!result || result.status !== 'ok') result = valuateLocal(input);
    valuationCache[signature] = result;
    return result;
  }

  function balanceOf(mineItem, theirsItem) {
    var payload = { mine: inputOf(mineItem), theirs: inputOf(theirsItem) };
    var result = null;
    try {
      if (window.Nahua && typeof Nahua.compare === 'function') result = Nahua.compare(payload.mine, payload.theirs);
    } catch (error) {
      result = null;
    }
    if (!result || result.status !== 'ok') {
      result = compareLocal(valuateLocal(payload.mine), valuateLocal(payload.theirs));
    }
    return result;
  }

  function announce(title, body) {
    try {
      if (window.Nahua && typeof Nahua.announce === 'function') {
        var pending = Nahua.announce({ title: title, body: body });
        if (pending && pending.catch) {
          pending.catch(function () { flash(title); });
        }
        return;
      }
    } catch (error) {
      // Sin anfitrión (navegador) no hay bandeja nativa: el aviso se queda en el flujo de trueques.
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Datos semilla
  // ---------------------------------------------------------------------------------------------

  function seedListing(list, owner, title, emoji, category, condition, ageMonths, newPrice, askPrice, barter, desc, complete, receipt) {
    var hours = 1000 * 60 * 60;
    return {
      id: list.id,
      ownerId: owner,
      title: title,
      description: desc || '',
      emoji: emoji,
      category: category,
      condition: condition,
      ageMonths: ageMonths,
      referenceCents: Math.round(newPrice * 100),
      askingCents: Math.round(askPrice * 100),
      barter: !!barter,
      complete: complete !== false,
      hasReceipt: !!receipt,
      status: 'DISPONIBLE',
      createdAt: Date.now() - (list.hoursAgo || 4) * hours
    };
  }

  function seed() {
    var now = Date.now();
    var hour = 1000 * 60 * 60;

    var users = {
      me: { id: 'me', name: 'Ana Nahua', handle: '@ana.nahua', colony: 'Colima Centro', rating: 4.9, trades: 9, since: '2023' },
      chelo: { id: 'chelo', name: 'Doña Chelo', handle: '@chelo.cocina', colony: 'Colima Centro', rating: 4.8, trades: 26, since: '2021' },
      beto: { id: 'beto', name: 'Beto Nieves', handle: '@beto.nieves', colony: 'Villa de Álvarez', rating: 4.6, trades: 14, since: '2022' },
      mari: { id: 'mari', name: 'Mari Preciado', handle: '@mari.p', colony: 'Comala', rating: 5.0, trades: 31, since: '2020' },
      chuy: { id: 'chuy', name: 'Don Chuy', handle: '@chuy.taller', colony: 'Tecomán', rating: 4.7, trades: 19, since: '2022' },
      fer: { id: 'fer', name: 'Fer Alejandrez', handle: '@fer.music', colony: 'Colima Centro', rating: 4.5, trades: 6, since: '2024' },
      tere: { id: 'tere', name: 'Tere Lomelí', handle: '@tere.lomeli', colony: 'Manzanillo', rating: 4.8, trades: 22, since: '2021' },
      jorge: { id: 'jorge', name: 'Jorge Rincón', handle: '@jorge.madera', colony: 'Villa de Álvarez', rating: 4.4, trades: 8, since: '2023' }
    };

    var listings = [
      seedListing({ id: 'L1', hoursAgo: 2 }, 'beto', 'Bicicleta rodada 26', '🚲', 'BIKES', 'GOOD', 36, 6500, 2900, true,
        'Bici de montaña con 21 velocidades. Le cambié los frenos hace un mes; detalles de pintura en el cuadro.', true, false),
      seedListing({ id: 'L2', hoursAgo: 5 }, 'chelo', 'Licuadora Oster 3 velocidades', '🥤', 'APPLIANCES', 'LIKE_NEW', 18, 1800, 850, true,
        'Casi sin uso, vaso de vidrio de 1.25 L. La cambio porque me regalaron otra.', true, true),
      seedListing({ id: 'L3', hoursAgo: 8 }, 'chuy', 'Taladro Bosch 550 W', '🔧', 'TOOLS', 'LIKE_NEW', 10, 2600, 1500, true,
        'Con estuche, brocas y manual. Lo usé en dos muebles nada más.', true, true),
      seedListing({ id: 'L4', hoursAgo: 11 }, 'tere', 'Laptop Lenovo ThinkPad', '💻', 'COMPUTERS', 'GOOD', 48, 14000, 6500, false,
        'i5, 8 GB de RAM, SSD de 256 GB. Batería al 70 %, cargador original incluido.', true, false),
      seedListing({ id: 'L5', hoursAgo: 20 }, 'mari', 'Máquina de coser Singer', '🧵', 'TOOLS', 'GOOD', 120, 5000, 1900, true,
        'Funciona perfecto, con pedal y accesorios. Era de mi mamá; la vendo porque no la uso.', true, false),
      seedListing({ id: 'L6', hoursAgo: 26 }, 'fer', 'Guitarra acústica Yamaha', '🎸', 'MUSIC', 'GOOD', 60, 3200, 1400, true,
        'Cuerdas nuevas, estuche de tela. Tiene marcas de uso en la caja, nada que afecte el sonido.', true, false),
      seedListing({ id: 'L7', hoursAgo: 30 }, 'jorge', 'Mesa de comedor de parota', '🪑', 'FURNITURE', 'GOOD', 96, 8000, 3200, true,
        'Mesa de 6 personas, madera maciza. La vendo porque me mudé a un depa más chico.', true, false),
      seedListing({ id: 'L8', hoursAgo: 36 }, 'chelo', 'Juego de sartenes de barro', '🍳', 'OTHER', 'NEW', 0, 900, 550, false,
        'Tres piezas nuevas, hechas en Suchitlán. Nunca se usaron.', true, false),
      seedListing({ id: 'L9', hoursAgo: 40 }, 'tere', 'Colección de monedas antiguas', '🪙', 'COLLECTIBLES', 'GOOD', 240, 3000, 2400, true,
        '28 monedas de plata y cobre, algunas de los años cincuenta. Con álbum de colección.', true, false),
      seedListing({ id: 'L10', hoursAgo: 44 }, 'beto', 'Bocina Bluetooth JBL', '🔊', 'ELECTRONICS', 'GOOD', 24, 1200, 600, false,
        'Suena muy bien, batería de 8 horas. Sin cargador original, usa USB-C normal.', true, false),
      seedListing({ id: 'L11', hoursAgo: 50 }, 'mari', 'Bicicleta fija para ejercicio', '🚴', 'SPORTS', 'WORN', 72, 3500, 1200, false,
        'Tiene marcas de uso y el monitor no enciende, pero la resistencia funciona bien.', false, false),
      seedListing({ id: 'L12', hoursAgo: 54 }, 'chuy', 'Caja de herramientas 40 piezas', '🧰', 'TOOLS', 'WORN', 48, 1200, 500, true,
        'Dados, desarmadores y pinzas. La caja tiene marcas de taller, todo funciona.', false, false),
      // Mis publicaciones
      seedListing({ id: 'L20', hoursAgo: 6 }, 'me', 'Mochila de senderismo 40 L', '🎒', 'SPORTS', 'LIKE_NEW', 8, 1900, 1100, true,
        'La compré para un viaje al Volcán y la usé dos veces. Impermeable, con cubre mochila.', true, true),
      seedListing({ id: 'L21', hoursAgo: 14 }, 'me', 'Cafetera de prensa francesa', '☕', 'APPLIANCES', 'GOOD', 30, 900, 450, true,
        'De vidrio grueso, 600 ml. Limpia y sin fisuras.', true, false),
      seedListing({ id: 'L22', hoursAgo: 22 }, 'me', 'Bocina Bluetooth pequeña', '🔊', 'ELECTRONICS', 'GOOD', 20, 800, 400, true,
        'Compacta, ideal para el patio. Incluye cable de carga.', true, false),
      seedListing({ id: 'L23', hoursAgo: 52 }, 'me', 'Juego de llaves mecánicas', '🔧', 'TOOLS', 'GOOD', 30, 1500, 700, true,
        'Doce piezas del 8 al 19 mm, en su estuche. Sin uso desde que me mudé.', true, false)
    ];

    var offers = [
      {
        id: 'o1', listingId: 'L20', offeredListingId: 'L1',
        fromUserId: 'beto', toUserId: 'me',
        message: 'Hola Ana, me interesa tu mochila para una ruta al Volcán. Te ofrezco mi bici, está al día. ¿Te late?',
        status: 'PENDIENTE', cashCents: 0, turnUserId: 'me', meeting: null,
        createdAt: now - 2 * hour, history: []
      },
      {
        id: 'o2', listingId: 'L22', offeredListingId: 'L12',
        fromUserId: 'chuy', toUserId: 'me',
        message: 'Buenas tardes. Ando buscando una bocina para el taller. Le ofrezco el taladro Bosch con todo y estuche.',
        status: 'PENDIENTE', cashCents: 0, turnUserId: 'me', meeting: null,
        createdAt: now - 7 * hour, history: []
      },
      {
        id: 'o3', listingId: 'L4', offeredListingId: 'L21',
        fromUserId: 'me', toUserId: 'tere',
        message: 'Tere, me interesa la ThinkPad. Te doy mi prensa francesa y puedo poner la diferencia.',
        status: 'CONTRAPROPUESTA', cashCents: 350000, turnUserId: 'me', meeting: null,
        createdAt: now - 26 * hour, history: []
      },
      {
        id: 'o4', listingId: 'L6', offeredListingId: 'L23',
        fromUserId: 'me', toUserId: 'fer',
        message: 'Fer, ¿te interesa cambiar la guitarra por el juego de llaves? Está completo.',
        status: 'ACEPTADO', cashCents: 0, turnUserId: null, meeting: null,
        createdAt: now - 49 * hour, history: []
      }
    ];

    return { version: 2, me: 'me', users: users, listings: listings, offers: offers };
  }

  // ---------------------------------------------------------------------------------------------
  // Estado
  // ---------------------------------------------------------------------------------------------

  function load() {
    try {
      var raw = window.localStorage.getItem(STORE_KEY);
      if (raw) {
        var parsed = JSON.parse(raw);
        if (parsed && parsed.version === 2 && parsed.users && parsed.listings && parsed.offers) {
          return parsed;
        }
      }
    } catch (error) {
      // datos corruptos: se vuelve a sembrar el bazar
    }
    return seed();
  }

  function save() {
    try {
      window.localStorage.setItem(STORE_KEY, JSON.stringify(data));
    } catch (error) {
      // sin espacio: la sesión sigue funcionando en memoria
    }
  }

  var data = load();

  var ui = {
    route: { name: 'feed' },
    filter: 'all',
    query: '',
    tradePick: null,
    tradeMessage: '',
    cash: '',
    form: freshForm()
  };

  // Pila de navegación: "Ver perfil" desde un trato regresa al trato, no al inicio.
  var history = [];

  function freshForm() {
    return {
      title: '',
      description: '',
      emoji: '📦',
      category: 'TOOLS',
      condition: 'GOOD',
      ageMonths: 0,
      reference: '',
      asking: '',
      complete: true,
      hasReceipt: false,
      barter: false
    };
  }

  function me() {
    return data.users[data.me] || { id: 'me', name: 'Ana Nahua', handle: '@ana.nahua', colony: 'Colima Centro', rating: 5, trades: 0 };
  }

  function user(id) {
    return data.users[id] || { id: id, name: 'Vecino', handle: '@vecino', colony: 'Colima', rating: 5, trades: 0 };
  }

  function listing(id) {
    for (var i = 0; i < data.listings.length; i++) if (data.listings[i].id === id) return data.listings[i];
    return null;
  }

  function offer(id) {
    for (var i = 0; i < data.offers.length; i++) if (data.offers[i].id === id) return data.offers[i];
    return null;
  }

  function myBarterables() {
    return data.listings.filter(function (item) {
      return item.ownerId === data.me && item.barter && item.status === 'DISPONIBLE';
    });
  }

  function waitingOnMe(offerItem) {
    return offerItem.turnUserId === data.me &&
      (offerItem.status === 'PENDIENTE' || offerItem.status === 'CONTRAPROPUESTA');
  }

  function pendingCount() {
    return data.offers.filter(function (item) {
      return item.toUserId === data.me &&
        (item.status === 'PENDIENTE' || item.status === 'CONTRAPROPUESTA') &&
        item.turnUserId === data.me;
    }).length;
  }

  function statusLabel(status) {
    if (status === 'PENDIENTE') return 'Pendiente';
    if (status === 'CONTRAPROPUESTA') return 'Contraoferta';
    if (status === 'ACEPTADO') return 'Trato cerrado';
    if (status === 'RECHAZADO') return 'Rechazado';
    if (status === 'CANCELADO') return 'Cancelado';
    if (status === 'CERRADO') return 'Intercambiado';
    return status;
  }

  function statusClass(status) {
    if (status === 'CONTRAPROPUESTA') return 'contrapropuesta';
    return String(status || '').toLowerCase();
  }

  function activeListingsFor(ownerId) {
    return data.listings.filter(function (item) {
      return item.ownerId === ownerId && item.status !== 'OCULTO';
    });
  }

  // ---------------------------------------------------------------------------------------------
  // Avisos flotantes
  // ---------------------------------------------------------------------------------------------

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
    }, 2400);
  }

  // ---------------------------------------------------------------------------------------------
  // Vistas
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

  function filteredListings() {
    var query = ui.query.trim().toLowerCase();
    var items = data.listings.filter(function (item) {
      if (item.status === 'VENDIDO' || item.status === 'TRUEQUEADO') return false;
      if (ui.filter === 'barter' && !(item.barter && item.status === 'DISPONIBLE')) return false;
      if (ui.filter === 'venta' && item.barter) return false;
      if (ui.filter === 'mine' && item.ownerId !== data.me) return false;
      if (!query) return true;
      var owner = user(item.ownerId);
      var haystack = [item.title, item.description, categoryById(item.category).label, owner.name, owner.colony]
        .join(' ').toLowerCase();
      return haystack.indexOf(query) >= 0;
    });
    return items.sort(function (a, b) { return (b.createdAt || 0) - (a.createdAt || 0); });
  }

  function listTitle() {
    if (ui.filter === 'barter') return 'Objetos en trueque';
    if (ui.filter === 'venta') return 'Sólo venta';
    if (ui.filter === 'mine') return 'Mis publicaciones';
    return 'Lo nuevo del bazar';
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

  function card(item) {
    var value = appraise(item);
    var owner = user(item.ownerId);
    var mine = item.ownerId === data.me;
    var canTrade = item.barter && !mine && item.status === 'DISPONIBLE';
    var statusBadge = '';
    if (item.status === 'RESERVADO') statusBadge = '<span class="badge badge-done">Apartado</span>';
    if (item.status === 'TRUEQUEADO') statusBadge = '<span class="badge badge-done">Truequeado</span>';
    if (item.status === 'VENDIDO') statusBadge = '<span class="badge badge-done">Vendido</span>';

    return '<article class="item-card" data-act="open" data-id="' + item.id + '">' +
      '<div class="thumb">' + item.emoji + (item.barter ? '<span class="flag">Trueque</span>' : '') + '</div>' +
      '<div class="item-body">' +
        '<h3>' + esc(item.title) + '</h3>' +
        '<div class="item-meta">' + esc(conditionById(item.condition).short) + ' · ' + esc(owner.name) +
          ' · ' + esc(owner.colony) + '</div>' +
        '<div class="item-values">' +
          '<div class="ask">' + money(item.askingCents) + '<small>pide</small></div>' +
          '<div class="est">' + money(value.estimatedCents) + '<small>valor estimado</small></div>' +
        '</div>' +
        '<div class="item-actions">' +
          (mine ? '<span class="badge badge-mine">Tu publicación</span>' : '') +
          (item.barter ? '<span class="badge badge-barter">Acepta trueque</span>' : '<span class="badge badge-sale">Sólo venta</span>') +
          statusBadge +
          (canTrade ? '<button class="btn rose sm" data-act="trade" data-id="' + item.id + '">Truquear</button>' : '') +
        '</div>' +
      '</div>' +
    '</article>';
  }

  function cardsHtml() {
    var items = filteredListings();
    if (!items.length) {
      return '<div class="empty"><div class="ico">🧺</div><h3>Nada por aquí todavía</h3>' +
        '<p>Cambia el filtro o publica el primer objeto de tu colonia.</p>' +
        '<button class="btn primary" data-act="tab" data-id="publish">Publicar un objeto</button></div>';
    }
    return items.map(card).join('');
  }

  function feedView() {
    var barterCount = data.listings.filter(function (item) {
      return item.barter && item.status === 'DISPONIBLE';
    }).length;
    var closed = data.offers.filter(function (item) {
      return item.status === 'ACEPTADO' || item.status === 'CERRADO';
    }).length;

    return '<section class="hero">' +
      '<p class="kicker">Comunidad · Colima</p>' +
      '<h1>Compra, vende y <em>truequea</em> con tus vecinos</h1>' +
      '<p class="lede">Publica lo que ya no usas. Lo valoramos para que sepas cuánto vale y tú decides si lo vendes o lo cambias.</p>' +
      '<div class="hero-stats">' +
        stat(data.listings.filter(function (item) { return item.status === 'DISPONIBLE'; }).length, 'objetos') +
        stat(barterCount, 'en trueque') +
        stat(closed, closed === 1 ? 'trato' : 'tratos') +
      '</div>' +
    '</section>' +
    '<div class="search"><span>⌕</span><input id="q" type="search" placeholder="Busca objeto, colonia o persona" value="' + esc(ui.query) + '"></div>' +
    filterChips() +
    '<div class="section-head"><h2 id="listTitle">' + esc(listTitle()) + '</h2><span id="listCount"></span></div>' +
    '<div class="cards" id="cards">' + cardsHtml() + '</div>' +
    '<p class="footer-note">El valor es una estimación: precio nuevo, estado, uso y accesorios.</p>';
  }

  function valuationPanel(value, options) {
    var settings = options || {};
    var factors = value.factors && value.factors.length
      ? '<ul class="factors">' + value.factors.map(function (factor) {
          return '<li class="' + factor.tone + '"><span>' + esc(factor.label) + '</span><b>' +
            (factor.percent > 0 ? '+' : '') + factor.percent + ' %</b></li>';
        }).join('') + '</ul>'
      : '<div class="hint-line" style="background:var(--card-alt);border-color:var(--line-soft)">Ingresa el precio de referencia para ver el desglose.</div>';

    return '<div class="panel">' +
      '<div class="panel-head"><h3>' + (settings.title || 'Valoración del objeto') + '</h3>' +
        '<span class="conf">confianza ' + esc(value.confidenceLabel) + '</span></div>' +
      '<div class="value-big tabular">' + money(value.estimatedCents) + '</div>' +
      '<div class="value-range">rango estimado ' + money(value.lowCents) + ' – ' + money(value.highCents) + '</div>' +
      factors +
      (value.hasValue ? '<div class="hint-line">⇄ Mínimo recomendado para trueque: <b class="tabular">' +
        money(value.barterMinCents) + '</b></div>' : '') +
    '</div>';
  }

  function ownerRow(userId) {
    var profile = user(userId);
    var mine = userId === data.me;
    return '<button class="owner" data-act="profile" data-id="' + profile.id + '">' +
      avatar(userId) +
      '<div>' +
        '<div class="owner-name">' + esc(profile.name) + (mine ? ' (tú)' : '') + '</div>' +
        '<div class="owner-sub">' + esc(profile.handle) + ' · ' + esc(profile.colony) + ' · ★ ' +
          profile.rating.toFixed(1) + ' · ' + profile.trades + ' tratos</div>' +
      '</div>' +
      '<span class="go">›</span>' +
    '</button>';
  }

  function itemView(id) {
    var item = listing(id);
    if (!item) return notFound('Ese objeto ya no está en el bazar.');
    var value = appraise(item);
    var owner = user(item.ownerId);
    var mine = item.ownerId === data.me;
    var available = item.status === 'DISPONIBLE';
    var actions = '';

    if (!mine && available) {
      if (item.barter) {
        actions += '<button class="btn rose block" data-act="trade" data-id="' + item.id + '">⇄ Truquear</button>';
      }
      actions += '<button class="btn primary block" data-act="buy" data-id="' + item.id + '">Comprar con cartera · ' +
        money(item.askingCents) + '</button>';
    } else if (mine) {
      actions += '<button class="badge badge-mine" style="align-self:flex-start">Tu publicación</button>';
      actions += '<button class="btn primary block" data-act="tab" data-id="trades">Ver trueques recibidos</button>';
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

    return backButton('Bazar', 'feed') +
      '<div class="hero-art">' + item.emoji +
        (item.barter ? '<span class="ribbon">Acepta trueque</span>' : '<span class="ribbon" style="background:var(--blue)">Sólo venta</span>') +
      '</div>' +
      '<h1 class="detail-title">' + esc(item.title) + '</h1>' +
      '<div class="detail-meta">' + esc(categoryById(item.category).label) + ' · ' +
        esc(conditionById(item.condition).label) + ' · ' + esc(ageLabel(item.ageMonths)) + '</div>' +
      '<div class="price-row">' +
        '<div class="price-main tabular">' + money(item.askingCents) + '<small>precio</small></div>' +
        '<span class="badge badge-barter">≈ ' + money(value.estimatedCents) + ' de valor</span>' +
      '</div>' +
      (item.description ? '<p class="desc">' + esc(item.description) + '</p>' : '') +
      (item.barter
        ? '<div class="hint-line" style="margin-top:10px">' + esc(owner.name) + ' abre este objeto a trueque: puedes ofrecerle algo de tu bazar.</div>'
        : '<div class="note">Este objeto no acepta trueque, pero puedes comprarlo con tu cartera Nahua.</div>') +
      '<div class="actions-block">' + actions + '</div>' +
      '<div style="margin-top:14px">' + valuationPanel(value) + '</div>' +
      '<div style="margin-top:12px">' + ownerRow(item.ownerId) + '</div>';
  }

  function tradesView() {
    var received = data.offers.filter(function (item) { return item.toUserId === data.me; });
    var sent = data.offers.filter(function (item) { return item.fromUserId === data.me; });
    var tab = ui.tradesTab === 'sent' ? 'sent' : 'received';
    var items = tab === 'sent' ? sent : received;

    var body = items.length
      ? items.map(function (item) { return offerCard(item, tab === 'sent'); }).join('')
      : '<div class="empty"><div class="ico">⇄</div><h3>' +
          (tab === 'sent' ? 'No has propuesto trueques' : 'No tienes propuestas') + '</h3>' +
          '<p>' + (tab === 'sent'
            ? 'Encuentra un objeto que acepte trueque y pulsa Truquear.'
            : 'Cuando alguien quiera truequear contigo, el aviso llega aquí y a tu bandeja de Avisos.') + '</p>' +
          '<button class="btn primary" data-act="tab" data-id="feed">Ver el bazar</button></div>';

    return '<p class="kicker">Tratos</p>' +
      '<h1 style="font-size:20px;margin-top:4px">Trueques de la comunidad</h1>' +
      '<p class="lede" style="margin-bottom:0">Cada propuesta compara el valor de los dos objetos antes de aceptar.</p>' +
      '<div class="segmented">' +
        '<button class="' + (tab === 'received' ? 'active' : '') + '" data-act="trades-tab" data-id="received">Recibidas (' + received.length + ')</button>' +
        '<button class="' + (tab === 'sent' ? 'active' : '') + '" data-act="trades-tab" data-id="sent">Enviadas (' + sent.length + ')</button>' +
      '</div>' +
      '<div class="cards" style="margin-top:12px">' + body + '</div>';
  }

  function offerCard(offerItem, sent) {
    var want = listing(offerItem.listingId);
    var give = listing(offerItem.offeredListingId);
    if (!want || !give) return '';
    var myItem = sent ? give : want;
    var theirItem = sent ? want : give;
    var balance = balanceOf(myItem, theirItem);
    var other = user(sent ? offerItem.toUserId : offerItem.fromUserId);
    var waiting = waitingOnMe(offerItem);

    var deltaClass = balance.verdict === 'favorable' ? 'up' : (balance.verdict === 'desfavorable' ? 'down' : 'even');
    var deltaText = balance.verdict === 'favorable'
      ? '+' + money(Math.abs(balance.deltaCents)) + ' a tu favor'
      : (balance.verdict === 'desfavorable' ? money(Math.abs(balance.deltaCents)) + ' en contra' : 'valores parejos');

    return '<article class="offer-card" data-act="deal" data-id="' + offerItem.id + '">' +
      '<div class="offer-top">' + avatar(other.id, '') +
        '<div class="t">' + (sent
          ? 'Tu propuesta a <b>' + esc(other.name) + '</b> por <b>' + esc(want.title) + '</b>'
          : '<b>' + esc(other.name) + '</b> quiere tu <b>' + esc(want.title) + '</b>') +
        '</div>' +
      '</div>' +
      '<div class="swap-row">' +
        '<div class="swap-item"><span class="lbl">' + (sent ? 'Ofreces' : 'Te ofrece') + '</span>' +
          '<span class="e">' + give.emoji + '</span><b>' + esc(give.title) + '</b>' +
          '<small>≈ ' + money(appraise(give).estimatedCents) + '</small></div>' +
        '<div class="swap-arrow">⇄</div>' +
        '<div class="swap-item"><span class="lbl">' + (sent ? 'Quieres' : 'Quiere') + '</span>' +
          '<span class="e">' + want.emoji + '</span><b>' + esc(want.title) + '</b>' +
          '<small>≈ ' + money(appraise(want).estimatedCents) + '</small></div>' +
      '</div>' +
      '<div class="offer-foot">' +
        '<span class="pill ' + statusClass(offerItem.status) + '">' + esc(statusLabel(offerItem.status)) + '</span>' +
        '<span class="delta ' + deltaClass + '">' + deltaText + '</span>' +
        '<span style="margin-left:auto;font-size:10px;color:var(--muted)">' + esc(ago(offerItem.createdAt)) + '</span>' +
      '</div>' +
      (waiting ? '<div class="offer-foot" style="margin-top:6px"><span class="delta up">Te toca responder →</span></div>' : '') +
    '</article>';
  }

  function verdictPanel(balance) {
    var icon = balance.verdict === 'favorable' ? '✓' : (balance.verdict === 'desfavorable' ? '!' : '⇄');
    return '<div class="verdict ' + balance.verdict + '">' +
      '<span class="vd-icon">' + icon + '</span>' +
      '<div><strong>' + esc(balance.verdictLabel) + '</strong><p>' + esc(balance.message) + '</p></div>' +
    '</div>';
  }

  function dealView(id) {
    var offerItem = offer(id);
    if (!offerItem) return notFound('Ese trato ya no existe.');
    var want = listing(offerItem.listingId);
    var give = listing(offerItem.offeredListingId);
    if (!want || !give) return notFound('Uno de los objetos del trato ya no está publicado.');

    var receiver = offerItem.toUserId === data.me;
    var mineItem = receiver ? want : give;
    var theirsItem = receiver ? give : want;
    var otherId = receiver ? offerItem.fromUserId : offerItem.toUserId;
    var other = user(otherId);
    var balance = balanceOf(mineItem, theirsItem);
    var myTurn = waitingOnMe(offerItem);
    var open = offerItem.status === 'PENDIENTE' || offerItem.status === 'CONTRAPROPUESTA';

    var head = backButton('Trueques', 'trades') +
      '<div class="offer-foot"><span class="pill ' + statusClass(offerItem.status) + '">' +
        esc(statusLabel(offerItem.status)) + '</span>' +
        '<span style="font-size:10px;color:var(--muted)">' + esc(ago(offerItem.createdAt)) + '</span></div>' +
      '<h1 class="detail-title">' + (receiver ? 'Te proponen un trueque' : 'Tu propuesta de trueque') + '</h1>' +
      '<p class="detail-meta">' + esc(receiver
        ? other.name + ' quiere tu ' + want.title
        : 'Ofreciste ' + give.title + ' a ' + other.name + ' por ' + want.title) + '</p>' +
      '<div class="deal-grid">' +
        '<div class="deal-box"><span class="l">Das</span><span class="e">' + mineItem.emoji + '</span>' +
          '<span class="n">' + esc(mineItem.title) + '</span><span class="v tabular">' + money(appraise(mineItem).estimatedCents) + '</span></div>' +
        '<div class="deal-vs">⇄</div>' +
        '<div class="deal-box"><span class="l">Recibes</span><span class="e">' + theirsItem.emoji + '</span>' +
          '<span class="n">' + esc(theirsItem.title) + '</span><span class="v tabular">' + money(appraise(theirsItem).estimatedCents) + '</span></div>' +
      '</div>' +
      verdictPanel(balance) +
      (offerItem.message ? '<div class="quote">“' + esc(offerItem.message) + '”</div>' : '') +
      (offerItem.cashCents > 0
        ? '<div class="panel" style="margin-top:10px;background:var(--gold-soft);border-color:#E6C58A">' +
            '<div class="panel-head"><h3>Compensación en efectivo</h3></div>' +
            '<div class="value-big tabular">' + money(offerItem.cashCents) + '</div>' +
            '<div class="value-range">' + (receiver ? 'La pides tú' : 'La pide ' + esc(other.name)) +
            ' para emparejar el valor de los objetos.</div>' +
          '</div>'
        : '');

    var actions = '';
    if (open && myTurn) {
      actions += '<button class="btn block" data-act="profile" data-id="' + otherId + '">Ver perfil de ' +
        esc(other.name.split(' ')[0]) + ' y lo que ofrece</button>';
      actions += '<button class="btn green block" data-act="accept" data-id="' + offerItem.id + '">✓ Aceptar trato' +
        (offerItem.cashCents > 0 ? ' con ' + money(offerItem.cashCents) : '') + '</button>';
      actions += '<div class="panel" style="margin-top:0">' +
        '<div class="panel-head"><h3>Pedir compensación</h3></div>' +
        '<p style="font-size:11.5px;color:var(--ink-soft);margin-top:4px">' +
          (balance.cashTopUpCents > 0
            ? 'Si sientes que faltó valor, pide la diferencia en efectivo. El sistema sugiere ' + money(balance.cashTopUpCents) + '.'
            : 'Los valores están parejos. Si aún así quieres efectivo, escribe cuánto.') +
        '</p>' +
        '<div class="field"><input id="cashInput" type="number" inputmode="numeric" placeholder="Ej. 250" value="' +
          esc(ui.cash) + '"><div class="help">Pesos, sin centavos.</div></div>' +
        '<button class="btn block" data-act="ask-cash" data-id="' + offerItem.id + '">Pedir esa diferencia</button>' +
      '</div>';
      actions += '<button class="btn danger block" data-act="reject" data-id="' + offerItem.id + '">Rechazar</button>';
    } else if (open) {
      actions += '<div class="note">Esperando respuesta de <b>' + esc(other.name) + '</b>. Te avisaremos en Avisos.</div>';
      actions += '<button class="btn block" data-act="profile" data-id="' + otherId + '">Ver perfil de ' + esc(other.name.split(' ')[0]) + '</button>';
      actions += '<button class="btn danger block" data-act="cancel" data-id="' + offerItem.id + '">Cancelar propuesta</button>';
    } else if (offerItem.status === 'ACEPTADO') {
      actions += '<div class="panel" style="background:var(--green-soft);border-color:#B9D2AA">' +
        '<div class="panel-head"><h3>¡Trato cerrado!</h3></div>' +
        '<ol class="steps">' +
          '<li>Acuerden día y hora para el intercambio.</li>' +
          '<li>Revisen los dos objetos antes de cerrar.</li>' +
          (offerItem.cashCents > 0 ? '<li>Quien recibe paga ' + money(offerItem.cashCents) + ' en efectivo.</li>' : '') +
          '<li>Marquen el trueque como completado al terminar.</li>' +
        '</ol></div>';
      actions += '<div class="field"><label>Punto de encuentro</label>' +
        '<select id="meetingSelect">' + MEETING_POINTS.map(function (point) {
          return '<option' + (offerItem.meeting === point ? ' selected' : '') + '>' + esc(point) + '</option>';
        }).join('') + '</select>' +
        '<div class="help">Lugar público y con gente: jardín, mercado o portal.</div></div>';
      actions += '<button class="btn green block" data-act="complete" data-id="' + offerItem.id + '">Marcar como intercambiado</button>';
      actions += '<button class="btn block" data-act="profile" data-id="' + otherId + '">Ver perfil de ' + esc(other.name.split(' ')[0]) + '</button>';
    } else if (offerItem.status === 'RECHAZADO' || offerItem.status === 'CANCELADO') {
      actions += '<div class="note">' + (offerItem.status === 'CANCELADO' ? 'Cancelaste esta propuesta.' : 'La propuesta no prosperó.') +
        ' Puedes buscar otro objeto con valor parecido.</div>';
      actions += '<button class="btn primary block" data-act="tab" data-id="feed">Ver el bazar</button>';
    } else if (offerItem.status === 'CERRADO') {
      actions += '<div class="panel" style="background:var(--green-soft);border-color:#B9D2AA;text-align:center">' +
        '<div style="font-size:30px">🤝</div>' +
        '<h3 style="margin-top:6px">Trueque completado</h3>' +
        '<p style="font-size:12px;color:var(--ink-soft);margin-top:4px">' +
          esc(mineItem.title) + ' ⇄ ' + esc(theirsItem.title) + '</p>' +
        (offerItem.meeting ? '<p style="font-size:11px;color:var(--muted);margin-top:6px">' + esc(offerItem.meeting) + '</p>' : '') +
      '</div>';
      actions += '<button class="btn primary block" data-act="tab" data-id="feed">Seguir en el bazar</button>';
    }

    return head + '<div class="actions-block">' +
      actions + '</div>';
  }

  function profileView(id) {
    var profile = user(id);
    var mine = id === data.me;
    var items = activeListingsFor(id);
    var barterables = items.filter(function (item) {
      return item.barter && item.status === 'DISPONIBLE';
    });

    var cards = items.length
      ? items.map(function (item) {
          var value = appraise(item);
          var canTrade = !mine && item.barter && item.status === 'DISPONIBLE';
          return '<article class="item-card" data-act="open" data-id="' + item.id + '">' +
            '<div class="thumb">' + item.emoji + (item.barter ? '<span class="flag">Trueque</span>' : '') + '</div>' +
            '<div class="item-body"><h3>' + esc(item.title) + '</h3>' +
              '<div class="item-meta">' + esc(conditionById(item.condition).short) + ' · ≈ ' + money(value.estimatedCents) + ' de valor</div>' +
              '<div class="item-actions">' +
                (item.barter ? '<span class="badge badge-barter">Acepta trueque</span>' : '<span class="badge badge-sale">Sólo venta</span>') +
                (canTrade ? '<button class="btn rose sm" data-act="trade" data-id="' + item.id + '">Truquear</button>' : '') +
              '</div></div></article>';
        }).join('')
      : '<div class="empty"><div class="ico">📦</div><h3>' + (mine ? 'Aún no publicas nada' : 'Sin objetos publicados') + '</h3>' +
        '<p>' + (mine ? 'Publica tu primer objeto y decide si lo vendes o lo abres a trueque.' : 'Este vecino no tiene objetos disponibles.') + '</p>' +
        (mine ? '<button class="btn primary" data-act="tab" data-id="publish">Publicar un objeto</button>' : '') + '</div>';

    return backButton(mine ? 'Bazar' : 'Volver', 'feed') +
      '<div class="profile-head">' + avatar(id, 'lg') +
        '<div><h2>' + esc(profile.name) + (mine ? ' (tú)' : '') + '</h2>' +
          '<div class="p-sub">' + esc(profile.handle) + ' · ' + esc(profile.colony) + '</div>' +
          '<div class="p-sub">★ ' + profile.rating.toFixed(1) + ' · ' + profile.trades + ' tratos · desde ' + esc(profile.since || '2023') + '</div>' +
        '</div>' +
      '</div>' +
      '<div class="profile-stats">' +
        stat(items.length, 'objetos') +
        stat(barterables.length, 'en trueque') +
        stat(profile.trades, 'tratos') +
      '</div>' +
      (mine
        ? '<div class="note">Así te ven los demás en el bazar. Tus objetos con trueque habilitado aparecen aquí cuando alguien pulsa Truquear.</div>'
        : '<div class="note">Estos son los objetos que <b>' + esc(profile.name.split(' ')[0]) +
          '</b> tiene disponibles para truequear. Si te ofrecen un trato, revisa el valor de lo que das y lo que recibes.</div>') +
      '<div class="section-head"><h2>' + (mine ? 'Mi bazar' : 'Su bazar') + '</h2><span>' + barterables.length + ' en trueque</span></div>' +
      '<div class="cards">' + cards + '</div>';
  }

  function tradePickView(targetId) {
    var target = listing(targetId);
    if (!target) return notFound('Ese objeto ya no está disponible.');
    var targetValue = appraise(target);
    var candidates = myBarterables().filter(function (item) { return item.id !== target.id; });
    if (!candidates.length) {
      return backButton('Objeto', 'item') +
        '<h1 class="detail-title">Ofrecer trueque</h1>' +
        '<p class="detail-meta">Quieres <b>' + esc(target.title) + '</b> de ' + esc(user(target.ownerId).name) + '.</p>' +
        '<div class="empty"><div class="ico">📦</div><h3>No tienes objetos en trueque</h3>' +
        '<p>Para proponer un trueque necesitas publicar algo con la opción de trueque habilitada.</p>' +
        '<button class="btn primary" data-act="tab" data-id="publish">Publicar con trueque</button></div>';
    }

    var chosen = listing(ui.tradePick);
    if (!chosen || chosen.ownerId !== data.me || !chosen.barter) {
      chosen = candidates[0];
      ui.tradePick = chosen.id;
    }

    var balance = balanceOf(chosen, target);
    var options = candidates.map(function (item) {
      var value = appraise(item);
      return '<button class="owner" style="margin-top:6px;border-color:' +
        (item.id === chosen.id ? 'var(--rose)' : 'var(--line)') + '" data-act="pick" data-id="' + item.id + '">' +
        '<div class="thumb" style="width:42px;height:42px;font-size:20px">' + item.emoji + '</div>' +
        '<div><div class="owner-name">' + esc(item.title) + '</div>' +
          '<div class="owner-sub">≈ ' + money(value.estimatedCents) + ' de valor</div></div>' +
        '<span class="go" style="color:' + (item.id === chosen.id ? 'var(--rose)' : 'var(--line)') + '">' +
          (item.id === chosen.id ? '●' : '○') + '</span>' +
      '</button>';
    }).join('');

    var duplicate = data.offers.some(function (item) {
      return item.fromUserId === data.me && item.listingId === target.id &&
        (item.status === 'PENDIENTE' || item.status === 'CONTRAPROPUESTA' || item.status === 'ACEPTADO');
    });

    return backButton('Objeto', 'item') +
      '<p class="kicker">Trueque</p>' +
      '<h1 class="detail-title" style="margin-top:4px">Ofrecer trueque a ' + esc(user(target.ownerId).name.split(' ')[0]) + '</h1>' +
      '<p class="detail-meta">' + esc(user(target.ownerId).name.split(' ')[0]) + ' pide ' + money(target.askingCents) + ' por <b>' + esc(target.title) +
        '</b> · valor estimado ' + money(targetValue.estimatedCents) + '.</p>' +
      '<div class="section-head"><h2>Elige qué ofreces</h2><span>' + candidates.length + ' disponibles</span></div>' +
      options +
      '<div style="margin-top:12px">' + verdictPanel(balance) + '</div>' +
      '<div class="field"><label>Mensaje para ' + esc(user(target.ownerId).name.split(' ')[0]) + '</label>' +
        '<textarea id="tradeMsg" placeholder="Cuéntale por qué te interesa y en qué estado está tu objeto">' +
          esc(ui.tradeMessage || ('Hola, me interesa tu ' + target.title + '. Te ofrezco mi ' + chosen.title +
            ', en ' + conditionById(chosen.condition).short.toLowerCase() + '. ¿Te late?')) +
        '</textarea>' +
        '<div class="help">Al enviar, la app avisa a ' + esc(user(target.ownerId).name) + ' en su bandeja de Avisos.</div>' +
      '</div>' +
      (duplicate ? '<div class="tag-hint">Ya tienes una propuesta abierta por este objeto. Revísala en Trueques.</div>' : '') +
      '<div class="actions-block">' +
        '<button class="btn rose block" data-act="send-offer" data-id="' + target.id + '"' +
          (duplicate ? ' disabled' : '') + '>⇄ Enviar propuesta de trueque</button>' +
        '<button class="btn block" data-act="tab" data-id="trades">Ver mis trueques</button>' +
      '</div>';
  }

  function publishView() {
    var form = ui.form;
    var value = appraise({ referenceCents: pesosToCents(form.reference), category: form.category,
      condition: form.condition, ageMonths: form.ageMonths, complete: form.complete, hasReceipt: form.hasReceipt });

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
      '<p class="lede">Lo valoramos para que sepas cuánto vale. El trueque se activa cuando tú quieras.</p>' +

      '<div class="field"><label>Título</label>' +
        '<input id="fTitle" type="text" maxlength="60" placeholder="Ej. Bicicleta rodada 26" value="' + esc(form.title) + '"></div>' +

      '<div class="field"><label>Descripción</label>' +
        '<textarea id="fDesc" maxlength="240" placeholder="Estado real, qué incluye, por qué lo dejas ir">' + esc(form.description) + '</textarea></div>' +

      '<div class="field"><label>Ícono</label><div class="emoji-grid">' + emojis + '</div></div>' +

      '<div class="field"><label>Categoría</label><div class="option-grid">' + categories + '</div></div>' +

      '<div class="field"><label>Estado</label><div class="option-grid">' + conditions + '</div></div>' +

      '<div class="grid-2">' +
        '<div class="field"><label>Meses de uso</label>' +
          '<input id="fAge" type="number" inputmode="numeric" min="0" max="360" value="' + esc(form.ageMonths) + '"></div>' +
        '<div class="field"><label>Precio nuevo / referencia</label>' +
          '<input id="fRef" type="number" inputmode="numeric" placeholder="Ej. 1800" value="' + esc(form.reference) + '"></div>' +
      '</div>' +

      '<div class="switch-row">' +
        '<div class="txt"><strong>¿Conservas caja y accesorios?</strong><span>Suma valor si está completo</span></div>' +
        '<button class="switch' + (form.complete ? ' on' : '') + '" data-act="toggle-complete" aria-label="Caja y accesorios"></button>' +
      '</div>' +

      '<div class="switch-row">' +
        '<div class="txt"><strong>¿Tienes factura o garantía?</strong><span>Da confianza y suma valor</span></div>' +
        '<button class="switch' + (form.hasReceipt ? ' on' : '') + '" data-act="toggle-receipt" aria-label="Factura"></button>' +
      '</div>' +

      '<div id="valuation" style="margin-top:12px">' + valuationPanel(value, { title: 'Valoración en vivo' }) + '</div>' +

      '<div class="field"><label>Precio al que lo vendes</label>' +
        '<input id="fAsk" type="number" inputmode="numeric" placeholder="Ej. 700" value="' + esc(form.asking) + '">' +
        '<div class="help" id="askHelp">Si lo dejas vacío tomamos el valor estimado: <b>' + money(value.estimatedCents) + '</b>' +
          ' <button class="btn sm" style="padding:2px 7px;font-size:10px;margin-left:6px" data-act="use-estimate">Usar valor</button></div>' +
        '<div id="priceHint"></div>' +
      '</div>' +

      '<div class="switch-row" style="margin-top:14px">' +
        '<div class="txt"><strong>Acepto trueque</strong>' +
          '<span>' + (value.hasValue
            ? 'Tu objeto entra al intercambio de la comunidad'
            : 'Primero valora el objeto para poder activar el trueque') + '</span></div>' +
        '<button class="switch' + (form.barter ? ' on' : '') + (value.hasValue ? '' : ' disabled') +
          '" data-act="toggle-barter" aria-label="Acepto trueque"' + (value.hasValue ? '' : ' disabled') + '></button>' +
      '</div>' +
      (value.hasValue && form.barter
        ? '<div class="hint-line">Aceptarás trueques desde <b class="tabular">' + money(value.barterMinCents) +
          '</b> de valor. Si te ofrecen menos, el sistema te propondrá pedir la diferencia en efectivo.</div>'
        : '') +

      '<div class="actions-block">' +
        '<button class="btn primary block" data-act="publish">Publicar en el bazar</button>' +
      '</div>' +
      '<p class="footer-note">Tus publicaciones se guardan en este dispositivo.</p>';
  }

  function howView() {
    return backButton('Bazar', 'feed') +
      '<p class="kicker">Cómo funciona</p>' +
      '<h1 style="font-size:21px;margin-top:4px">Un tianguis donde todos pueden vender… y truequear</h1>' +
      '<div class="panel" style="margin-top:12px">' +
        '<ol class="steps">' +
          '<li><b>Publica</b> lo que ya no usas y decide si aceptas trueque. Si no habilitas la opción, sólo se vende.</li>' +
          '<li><b>Valoramos</b> el objeto con el precio nuevo, el estado, el uso y los accesorios. Verás un rango y el mínimo que conviene aceptar.</li>' +
          '<li><b>Truquear</b> avisa al dueño que te interesa. Él ve tu perfil y los objetos que tienes disponibles para truequear.</li>' +
          '<li><b>Cierra el trato</b>: si los valores no emparejan, se pide la diferencia en efectivo. Al aceptar, la app propone punto de encuentro y marca el intercambio.</li>' +
        '</ol>' +
      '</div>' +
      '<div class="note" style="margin-top:12px">El valor es una estimación comunitaria, no una tasación formal. Sirve para comparar objetos y evitar que un trueque salga muy disparejo.</div>' +
      '<div class="panel" style="margin-top:12px">' +
        '<div class="panel-head"><h3>Ejemplo</h3></div>' +
        '<p style="font-size:12px;color:var(--ink-soft);margin-top:6px">Das una licuadora valorada en $880 y te ofrecen una bici de $2,600. ' +
          'La app marca el trueque <b>a tu favor</b>. Si fuera al revés, te pediría compensar la diferencia en efectivo.</p>' +
      '</div>' +
      '<div class="actions-block">' +
        '<button class="btn primary block" data-act="tab" data-id="publish">Publicar mi primer objeto</button>' +
      '</div>';
  }

  function notFound(message) {
    return '<div class="empty" style="margin-top:20px"><div class="ico">🧭</div><h3>No encontramos eso</h3>' +
      '<p>' + esc(message || 'Puede que ya no esté publicado.') + '</p>' +
      '<button class="btn primary" data-act="tab" data-id="feed">Volver al bazar</button></div>';
  }

  // ---------------------------------------------------------------------------------------------
  // Pintar
  // ---------------------------------------------------------------------------------------------

  var lastRouteKey = '';

  function routeKey(route) {
    return route.name + ':' + (route.id || '');
  }

  function render() {
    var route = ui.route;
    var html;
    if (route.name === 'feed') html = feedView();
    else if (route.name === 'item') html = itemView(route.id);
    else if (route.name === 'trades') html = tradesView();
    else if (route.name === 'deal') html = dealView(route.id);
    else if (route.name === 'profile') html = profileView(route.id);
    else if (route.name === 'trade') html = tradePickView(route.id);
    else if (route.name === 'publish') html = publishView();
    else if (route.name === 'how') html = howView();
    else html = feedView();

    var main = document.getElementById('main');
    main.innerHTML = html;

    var key = routeKey(route);
    if (key !== lastRouteKey) {
      lastRouteKey = key;
      window.scrollTo(0, 0);
    }

    updateTabs();
    if (route.name === 'feed') wireSearch();
    if (route.name === 'publish') wirePublish();
    if (route.name === 'deal') wireDeal();
    if (route.name === 'trade') wireTrade();
  }

  function updateTabs() {
    var tabOf = {
      feed: 'feed', item: 'feed', how: 'feed', trade: 'feed',
      trades: 'trades', deal: 'trades',
      publish: 'publish',
      profile: 'me'
    };
    var rootTab = tabOf[ui.route.name] || 'feed';

    var buttons = document.querySelectorAll('.tabbar button');
    for (var i = 0; i < buttons.length; i++) {
      var id = buttons[i].getAttribute('data-id');
      buttons[i].className = id === rootTab ? 'active' : '';
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

  // ---------------------------------------------------------------------------------------------
  // Formulario de publicación: sólo se repinta la valoración al escribir
  // ---------------------------------------------------------------------------------------------

  function wirePublish() {
    var title = document.getElementById('fTitle');
    var desc = document.getElementById('fDesc');
    var age = document.getElementById('fAge');
    var reference = document.getElementById('fRef');
    var asking = document.getElementById('fAsk');

    if (title) title.addEventListener('input', function () { ui.form.title = title.value; });
    if (desc) desc.addEventListener('input', function () { ui.form.description = desc.value; });
    if (age) age.addEventListener('input', function () { ui.form.ageMonths = Math.max(0, Number(age.value) || 0); refreshValuation(); });
    if (reference) reference.addEventListener('input', function () { ui.form.reference = reference.value; refreshValuation(); });
    if (asking) asking.addEventListener('input', function () { ui.form.asking = asking.value; refreshPriceHint(); });
  }

  function formValue() {
    var form = ui.form;
    return {
      referenceCents: pesosToCents(form.reference),
      category: form.category,
      condition: form.condition,
      ageMonths: form.ageMonths,
      complete: form.complete,
      hasReceipt: form.hasReceipt
    };
  }

  function refreshValuation() {
    var value = appraise(formValue());
    var host = document.getElementById('valuation');
    if (host) host.innerHTML = valuationPanel(value, { title: 'Valoración en vivo' });

    var toggle = document.querySelector('[data-act="toggle-barter"]');
    if (toggle) {
      toggle.className = 'switch' + (ui.form.barter ? ' on' : '') + (value.hasValue ? '' : ' disabled');
      if (value.hasValue) toggle.removeAttribute('disabled');
      else toggle.setAttribute('disabled', 'disabled');
    }

    var hint = document.querySelector('[data-act="toggle-barter"]');
    if (hint && hint.parentNode) {
      var subtitle = hint.parentNode.querySelector('.txt span');
      if (subtitle) {
        subtitle.textContent = value.hasValue
          ? 'Tu objeto entra al intercambio de la comunidad'
          : 'Primero valora el objeto para poder activar el trueque';
      }
    }

    var help = document.getElementById('askHelp');
    if (help) {
      help.innerHTML = 'Si lo dejas vacío tomamos el valor estimado: <b>' + money(value.estimatedCents) + '</b> ' +
        '<button class="btn sm" style="padding:2px 7px;font-size:10px;margin-left:6px" data-act="use-estimate">Usar valor</button>';
    }

    refreshPriceHint();
  }

  function refreshPriceHint() {
    var host = document.getElementById('priceHint');
    if (!host) return;
    var value = appraise(formValue());
    var ask = pesosToCents(ui.form.asking) || value.estimatedCents;
    if (!value.hasValue) { host.innerHTML = ''; return; }
    if (ask > value.highCents * 1.15) {
      host.innerHTML = '<div class="tag-hint">Está por arriba del valor estimado: puede espantar compradores y trueques.</div>';
    } else if (ask < value.lowCents * 0.7) {
      host.innerHTML = '<div class="tag-hint">Está muy por debajo del valor: podrías pedir más o abrirlo a trueque.</div>';
    } else {
      host.innerHTML = '';
    }
  }

  function wireTrade() {
    var message = document.getElementById('tradeMsg');
    if (message) message.addEventListener('input', function () { ui.tradeMessage = message.value; });
  }

  function wireDeal() {
    var cash = document.getElementById('cashInput');
    if (cash) cash.addEventListener('input', function () { ui.cash = cash.value; });
  }

  // ---------------------------------------------------------------------------------------------
  // Acciones
  // ---------------------------------------------------------------------------------------------

  function navigate(route) {
    if (ui.route) history.push(ui.route);
    ui.confirmRetire = null;
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
    ui.route = fallback || { name: 'feed' };
    render();
  }

  function goTab(tab) {
    history.length = 0;
    if (tab === 'feed') { ui.route = { name: 'feed' }; render(); }
    else if (tab === 'trades') { ui.route = { name: 'trades' }; render(); }
    else if (tab === 'publish') { ui.route = { name: 'publish' }; render(); }
    else if (tab === 'me') { ui.route = { name: 'profile', id: data.me }; render(); }
  }

  function sendOffer(targetId) {
    var target = listing(targetId);
    var chosen = listing(ui.tradePick);
    if (!target || !chosen) { flash('Elige qué vas a ofrecer'); return; }
    if (chosen.ownerId !== data.me || !chosen.barter) { flash('Ese objeto no está abierto a trueque'); return; }

    var duplicate = data.offers.some(function (item) {
      return item.fromUserId === data.me && item.listingId === target.id &&
        (item.status === 'PENDIENTE' || item.status === 'CONTRAPROPUESTA' || item.status === 'ACEPTADO');
    });
    if (duplicate) { flash('Ya tienes una propuesta abierta por este objeto'); return; }

    var message = (ui.tradeMessage || '').trim();
    if (!message) {
      message = 'Hola, me interesa tu ' + target.title + '. Te ofrezco mi ' + chosen.title + '. ¿Te late?';
    }

    var offerItem = {
      id: 'o_' + Date.now().toString(36),
      listingId: target.id,
      offeredListingId: chosen.id,
      fromUserId: data.me,
      toUserId: target.ownerId,
      message: message.slice(0, 240),
      status: 'PENDIENTE',
      cashCents: 0,
      turnUserId: target.ownerId,
      meeting: null,
      createdAt: Date.now(),
      history: []
    };
    data.offers.unshift(offerItem);
    save();

    var owner = user(target.ownerId);
    var balance = balanceOf(chosen, target);
    announce(
      me().name + ' quiere truequear tu ' + target.title,
      'Te ofrece ' + chosen.title + ' (' + money(appraise(chosen).estimatedCents) + '). ' + balance.message
    );
    try { if (window.Nahua && Nahua.toast) Nahua.toast('Propuesta enviada a ' + owner.name); } catch (error) { /* sin host */ }
    flash('Avisamos a ' + owner.name + ' que te interesa truquear');

    ui.tradeMessage = '';
    ui.tradePick = null;
    ui.tradesTab = 'sent';
    history = [{ name: 'trades' }];
    ui.route = { name: 'deal', id: offerItem.id };
    render();
  }

  function acceptOffer(offerId) {
    var offerItem = offer(offerId);
    if (!offerItem || !waitingOnMe(offerItem)) { flash('Este trato ya se movió'); return; }
    offerItem.status = 'ACEPTADO';
    offerItem.turnUserId = null;
    offerItem.history.push({ at: Date.now(), actor: data.me, action: 'aceptar' });

    var want = listing(offerItem.listingId);
    var give = listing(offerItem.offeredListingId);
    if (want) want.status = 'RESERVADO';
    if (give) give.status = 'RESERVADO';
    save();

    var other = user(offerItem.fromUserId === data.me ? offerItem.toUserId : offerItem.fromUserId);
    announce('Trato cerrado con ' + other.name, 'El trueque quedó aceptado. Acuerden punto de encuentro en el Bazar/Trueque.');
    flash('¡Trato cerrado! Acuerden el punto de encuentro');
    render();
  }

  function rejectOffer(offerId) {
    var offerItem = offer(offerId);
    if (!offerItem) return;
    offerItem.status = 'RECHAZADO';
    offerItem.turnUserId = null;
    offerItem.history.push({ at: Date.now(), actor: data.me, action: 'rechazar' });
    save();
    flash('Propuesta rechazada');
    render();
  }

  function cancelOffer(offerId) {
    var offerItem = offer(offerId);
    if (!offerItem) return;
    offerItem.status = 'CANCELADO';
    offerItem.turnUserId = null;
    offerItem.history.push({ at: Date.now(), actor: data.me, action: 'cancelar' });
    save();
    flash('Propuesta cancelada');
    render();
  }

  function askCash(offerId) {
    var offerItem = offer(offerId);
    if (!offerItem || !waitingOnMe(offerItem)) { flash('Este trato ya se movió'); return; }
    var cents = pesosToCents(ui.cash);
    if (cents <= 0) { flash('Escribe cuánto quieres en efectivo'); return; }

    var receiver = offerItem.toUserId === data.me;
    offerItem.status = 'CONTRAPROPUESTA';
    offerItem.cashCents = cents;
    offerItem.turnUserId = receiver ? offerItem.fromUserId : offerItem.toUserId;
    offerItem.history.push({ at: Date.now(), actor: data.me, action: 'contraoferta', cashCents: cents });
    save();

    var other = user(offerItem.turnUserId);
    announce(me().name + ' pide ' + money(cents) + ' de compensación',
      'En el trueque por ' + (listing(offerItem.listingId) ? listing(offerItem.listingId).title : 'un objeto') +
      ' pide esa diferencia en efectivo para emparejar el valor.');
    flash('Le pediste ' + money(cents) + ' a ' + other.name);
    ui.cash = '';
    render();
  }

  function completeOffer(offerId) {
    var offerItem = offer(offerId);
    if (!offerItem || offerItem.status !== 'ACEPTADO') return;
    var select = document.getElementById('meetingSelect');
    offerItem.meeting = select ? select.value : MEETING_POINTS[0];
    offerItem.status = 'CERRADO';
    offerItem.history.push({ at: Date.now(), actor: data.me, action: 'cerrar' });

    var want = listing(offerItem.listingId);
    var give = listing(offerItem.offeredListingId);
    if (want) want.status = 'TRUEQUEADO';
    if (give) give.status = 'TRUEQUEADO';

    if (data.users[offerItem.fromUserId]) data.users[offerItem.fromUserId].trades += 1;
    if (data.users[offerItem.toUserId]) data.users[offerItem.toUserId].trades += 1;
    save();
    flash('Trueque completado. ¡Gracias por mover la comunidad!');
    render();
  }

  function buyListing(id) {
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
    }).then(function (result) {
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

  function publishListing() {
    var form = ui.form;
    var title = (form.title || '').trim();
    if (title.length < 4) { flash('Ponle un nombre de al menos 4 letras a tu objeto'); return; }

    var value = appraise(formValue());
    if (!value.hasValue) { flash('Necesitamos el precio nuevo o de referencia para valorar el objeto'); return; }

    var asking = pesosToCents(form.asking) || value.estimatedCents;
    var item = {
      id: 'L_' + Date.now().toString(36),
      ownerId: data.me,
      title: title.slice(0, 60),
      description: (form.description || '').trim().slice(0, 240),
      emoji: form.emoji,
      category: form.category,
      condition: form.condition,
      ageMonths: Math.max(0, Math.round(form.ageMonths || 0)),
      referenceCents: pesosToCents(form.reference),
      askingCents: asking,
      barter: !!form.barter && value.hasValue,
      complete: !!form.complete,
      hasReceipt: !!form.hasReceipt,
      status: 'DISPONIBLE',
      createdAt: Date.now()
    };

    data.listings.unshift(item);
    save();
    flash(item.barter ? 'Publicado con trueque habilitado' : 'Publicado en el bazar');
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

  // ---------------------------------------------------------------------------------------------
  // Eventos
  // ---------------------------------------------------------------------------------------------

  function onAction(action, id) {
    if (action === 'tab') return goTab(id);
    if (action === 'back') {
      if (id === 'trades') return goBack({ name: 'trades' });
      if (id === 'item') return goBack({ name: 'item', id: ui.route.id });
      return goBack({ name: 'feed' });
    }
    if (action === 'open') return navigate({ name: 'item', id: id });
    if (action === 'profile') return navigate({ name: 'profile', id: id });
    if (action === 'deal') {
      return navigate({ name: 'deal', id: id });
    }
    if (action === 'trade') {
      ui.tradePick = null;
      ui.tradeMessage = '';
      return navigate({ name: 'trade', id: id });
    }
    if (action === 'pick') {
      ui.tradePick = id;
      render();
      return;
    }
    if (action === 'send-offer') return sendOffer(id);
    if (action === 'accept') return acceptOffer(id);
    if (action === 'reject') return rejectOffer(id);
    if (action === 'cancel') return cancelOffer(id);
    if (action === 'ask-cash') return askCash(id);
    if (action === 'complete') return completeOffer(id);
    if (action === 'buy') return buyListing(id);
    if (action === 'filter') { ui.filter = id; render(); return; }
    if (action === 'trades-tab') { ui.tradesTab = id; render(); return; }
    if (action === 'how') return navigate({ name: 'how' });
    if (action === 'publish') return publishListing();
    if (action === 'retire') {
      ui.confirmRetire = id;
      render();
      return;
    }
    if (action === 'retire-confirm') {
      ui.confirmRetire = null;
      return retireListing(id);
    }
    if (action === 'retire-cancel') {
      ui.confirmRetire = null;
      render();
      return;
    }
    if (action === 'set-emoji') { ui.form.emoji = id; render(); return; }
    if (action === 'set-cat') {
      ui.form.category = id;
      var category = categoryById(id);
      ui.form.emoji = category.emoji;
      render();
      return;
    }
    if (action === 'set-cond') { ui.form.condition = id; render(); return; }
    if (action === 'toggle-complete') { ui.form.complete = !ui.form.complete; render(); return; }
    if (action === 'toggle-receipt') { ui.form.hasReceipt = !ui.form.hasReceipt; render(); return; }
    if (action === 'toggle-barter') {
      var value = appraise(formValue());
      if (!value.hasValue) { flash('Primero valora el objeto para activar el trueque'); return; }
      ui.form.barter = !ui.form.barter;
      render();
      return;
    }
    if (action === 'use-estimate') {
      var estimated = appraise(formValue());
      ui.form.asking = String(centsToPesos(estimated.estimatedCents));
      render();
      return;
    }
    if (action === 'close') {
      if (window.Nahua && Nahua.close) Nahua.close();
    }
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

  // Bloquea el zoom con doble toque de los botones y evita el "ghost click" de la barra inferior.
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
        if (session.handle) mine.handle = session.handle;
      }
    } catch (error) {
      // sin sesión: se usa el perfil de la comunidad
    }

    var brand = document.getElementById('brandName');
    if (brand) brand.textContent = 'Bazar/Trueque';

    render();
  }

  start();
})();
