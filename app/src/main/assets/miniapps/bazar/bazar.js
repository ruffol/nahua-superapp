/*
 * Bazar/Trueque comunitario — lógica de la mini app
 * ---------------------------------------------------------------------------------------------
 * Regla del trueque (v3): el chat NO se abre con una propuesta unilateral. Cada quien marca
 * «me interesa» en los objetos del otro, en privado. Sólo cuando el interés es mutuo —yo marqué
 * algo suyo y la otra persona marcó algo mío— el sistema abre un match y avisa a los dos con
 * nombres y objetos. Así nadie truequea por compromiso ni por presión social: si la otra persona
 * nunca marcó nada tuyo, jamás se enteró de que te interesaba.
 *
 * El interés recibido se muestra como conteo anónimo («2 personas marcaron interés en tus
 * objetos»), sin nombres, hasta que hay match. El contacto es nombre + teléfono (se revela al
 * hacer match), nunca un @usuario.
 *
 * La valoración sigue siendo la misma: la calcula el anfitrión (BarterValuator.kt) por el puente
 * Nahua.valuate; valuateLocal() es el espejo para cuando la página corre fuera de la app.
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
    { id: 'TEXTILES', label: 'Textiles y bordados', emoji: '🧶', resale: 0.55, yearlyLoss: 0.04, floor: 0.40, demand: 1.06 },
    { id: 'CRAFTS', label: 'Artesanías', emoji: '🏺', resale: 0.60, yearlyLoss: 0.03, floor: 0.42, demand: 1.08 },
    { id: 'FOOD', label: 'Comida y conservas', emoji: '🫙', resale: 0.70, yearlyLoss: 0.08, floor: 0.30, demand: 1.05 },
    { id: 'PLANTS', label: 'Plantas y semillas', emoji: '🌱', resale: 0.55, yearlyLoss: 0.10, floor: 0.30, demand: 1.02 },
    { id: 'ART', label: 'Arte y pintura', emoji: '🎨', resale: 0.60, yearlyLoss: 0.02, floor: 0.40, demand: 1.05 },
    { id: 'SCHOOL', label: 'Escolar', emoji: '📓', resale: 0.35, yearlyLoss: 0.10, floor: 0.25, demand: 1.00 },
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

  var STORE_KEY = 'nahua.bazar.v3';
  var LEGACY_KEYS = ['nahua.bazar.v2', 'nahua.bazar.v1'];
  var EMOJIS = ['📦', '🧶', '🏺', '🫙', '🌱', '🎨', '📓', '🚲', '📱', '💻', '🔧', '🪑', '🎸', '🎧', '☕', '🧺', '🪙', '🎒', '🍳', '🚴'];
  var MEETING_POINTS = [
    'Explanada de la facultad',
    'Cafetería del campus',
    'Biblioteca, planta baja',
    'Jardín principal del centro',
    'Portal del mercado'
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
  // Datos semilla: comunidad estudiantil (600 alumnos de distintos municipios)
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
    var minute = 1000 * 60;

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
      // Lo mío: 5 publicaciones, 3 abiertas a trueque.
      seedListing({ id: 'M1', hoursAgo: 6 }, 'me', 'Mochila de senderismo 40 L', '🎒', 'SPORTS', 'LIKE_NEW', 8, 1900, 1100, true,
        'La compré para una práctica de campo y la usé dos veces. Impermeable, con cubre mochila.', true, true),
      seedListing({ id: 'M2', hoursAgo: 14 }, 'me', 'Cafetera de prensa francesa', '☕', 'APPLIANCES', 'GOOD', 30, 900, 450, true,
        'De vidrio grueso, 600 ml. Limpia y sin fisuras.', true, false),
      seedListing({ id: 'M3', hoursAgo: 22 }, 'me', 'Bocina Bluetooth pequeña', '🔊', 'ELECTRONICS', 'GOOD', 20, 800, 400, true,
        'Compacta, ideal para el cuarto. Incluye cable de carga.', true, false),
      seedListing({ id: 'M4', hoursAgo: 52 }, 'me', 'Juego de llaves mecánicas', '🔧', 'TOOLS', 'GOOD', 30, 1500, 700, false,
        'Doce piezas del 8 al 19 mm, en su estuche. Sin uso desde que me mudé.', true, false),
      seedListing({ id: 'M5', hoursAgo: 60 }, 'me', 'Apuntes y guías de Cálculo II', '📓', 'SCHOOL', 'GOOD', 12, 450, 180, false,
        'Carpeta con ejercicios resueltos y exámenes de tres semestres. Sirve para la carrera.', true, false),

      // Beto — Villa de Álvarez
      seedListing({ id: 'B1', hoursAgo: 3 }, 'beto', 'Bicicleta rodada 26', '🚲', 'BIKES', 'GOOD', 36, 6500, 2900, true,
        'Bici de montaña con 21 velocidades. Le cambié los frenos hace un mes; detalles de pintura en el cuadro.', true, false),
      seedListing({ id: 'B2', hoursAgo: 30 }, 'beto', 'Patineta de trucos', '🛹', 'SPORTS', 'WORN', 24, 1600, 550, false,
        'Ruedas nuevas, tabla raspada por el uso. Sirve para moverse al campus.', false, false),

      // Mari — Comala
      seedListing({ id: 'M6', hoursAgo: 8 }, 'mari', 'Blusa bordada de Suchitlán', '🧶', 'TEXTILES', 'LIKE_NEW', 4, 1200, 850, true,
        'Bordada a mano por mi tía en Suchitlán. Talla M, la usé una vez para una presentación.', true, false),
      seedListing({ id: 'M7', hoursAgo: 11 }, 'mari', 'Conserva de tamarindo (6 frascos)', '🫙', 'FOOD', 'NEW', 0, 750, 500, true,
        'Hecha en casa en Comala este mes. Sin conservadores, lista para acompañar el pan.', true, false),

      // Chuy — Suchitlán
      seedListing({ id: 'C1', hoursAgo: 5 }, 'chuy', 'Juego de ollas de barro de Suchitlán', '🏺', 'CRAFTS', 'NEW', 0, 1400, 900, true,
        'Cinco piezas hechas en el taller de la familia. Nuevas, listas para cocinar.', true, false),
      seedListing({ id: 'C2', hoursAgo: 26 }, 'chuy', 'Rebozo de telar de cintura', '🧶', 'TEXTILES', 'GOOD', 18, 2200, 1400, false,
        'Tejido en telar de cintura, tintes naturales. Tiene un hilo jalado en la orilla.', true, false),

      // Tere — Manzanillo
      seedListing({ id: 'T1', hoursAgo: 20 }, 'tere', 'Laptop Lenovo ThinkPad', '💻', 'COMPUTERS', 'GOOD', 48, 14000, 6500, false,
        'i5, 8 GB de RAM, SSD de 256 GB. Batería al 70 %, cargador original incluido.', true, false),
      seedListing({ id: 'T2', hoursAgo: 34 }, 'tere', 'Maleta de viaje 24"', '🧳', 'OTHER', 'GOOD', 24, 1800, 800, true,
        'Con ruedas y candado. La cambié por una más chica para viajar a Manzanillo.', true, false),

      // Fer — Colima
      seedListing({ id: 'F1', hoursAgo: 18 }, 'fer', 'Guitarra acústica Yamaha', '🎸', 'MUSIC', 'GOOD', 60, 3200, 1400, true,
        'Cuerdas nuevas, estuche de tela. Tiene marcas de uso en la caja, nada que afecte el sonido.', true, false),
      seedListing({ id: 'F2', hoursAgo: 40 }, 'fer', 'Cuadro de acuarela del Volcán', '🎨', 'ART', 'NEW', 0, 900, 650, false,
        'Pintado por mí, 30 × 40 cm con marco de madera. Firmado al reverso.', true, false),

      // Jorge — Ixtlahuacán
      seedListing({ id: 'J1', hoursAgo: 9 }, 'jorge', 'Semillas de maíz criollo (1 kg)', '🌱', 'PLANTS', 'NEW', 0, 600, 380, true,
        'De la milpa de mi abuelo en Ixtlahuacán. Para siembra en maceta o huerto escolar.', true, false),
      seedListing({ id: 'J2', hoursAgo: 44 }, 'jorge', 'Caja de herramientas 40 piezas', '🧰', 'TOOLS', 'WORN', 48, 1200, 500, true,
        'Dados, desarmadores y pinzas. La caja tiene marcas de taller, todo funciona.', false, false),

      // Lucía — Cuauhtémoc
      seedListing({ id: 'L1', hoursAgo: 12 }, 'lucia', 'Calculadora científica CASIO', '🖩', 'SCHOOL', 'LIKE_NEW', 10, 850, 500, true,
        'Con funda y manual. La compré para Estadística y ya uso la del celular.', true, true),
      seedListing({ id: 'L2', hoursAgo: 28 }, 'lucia', 'Café de Comala en grano (1 kg)', '☕', 'FOOD', 'NEW', 0, 700, 520, true,
        'Tostado medio, molido a pedido. Lo traje del rancho de mi familia.', true, false),
      seedListing({ id: 'L3', hoursAgo: 46 }, 'lucia', 'Plantas de orégano en maceta', '🌱', 'PLANTS', 'NEW', 0, 350, 220, false,
        'Tres macetas listas para la ventana de la casa. Con tierra preparada.', true, false)
    ];

    // Marcas de interés: privadas. Sólo los pares mutuos abren chat.
    var interest = function (id, listingId, fromUserId, minutesAgo) {
      return { id: id, listingId: listingId, fromUserId: fromUserId, createdAt: now - minutesAgo * minute };
    };
    var interests = [
      // Beto ↔ Ana: ya hay match
      interest('i1', 'M1', 'beto', 900),
      interest('i2', 'M2', 'beto', 880),
      interest('i3', 'B1', 'me', 860),
      // Mari ↔ Ana: ya hay match
      interest('i4', 'M3', 'mari', 430),
      interest('i5', 'M6', 'me', 420),
      interest('i6', 'M7', 'me', 400),
      // Fer ↔ Ana: trato ya cerrado
      interest('i7', 'M3', 'fer', 3300),
      interest('i8', 'F1', 'me', 3280),
      // Interés de una sola vía: no hay chat, Ana sólo ve el conteo anónimo
      interest('i9', 'M2', 'lucia', 210),
      interest('i10', 'M1', 'chuy', 150),
      interest('i11', 'M3', 'jorge', 70)
    ];

    var matches = [
      {
        id: 'x1', userA: 'me', userB: 'beto', status: 'CHAT', createdAt: now - 860 * minute,
        meeting: null, cashCents: 0, lastReadByMe: now - 30 * minute,
        messages: [
          { id: 'g1', fromUserId: 'system', kind: 'system', at: now - 860 * minute, text: 'Match de trueque: ambos marcaron interés. Ya pueden hablar sin compromiso.' },
          { id: 'g2', fromUserId: 'beto', kind: 'text', at: now - 820 * minute, text: 'Hola Ana. Vi que te interesa la bici y a mí tu mochila y la cafetera. ¿La bici te sirve para el campus o para ruta?' },
          { id: 'g3', fromUserId: 'me', kind: 'text', at: now - 700 * minute, text: 'Para el campus, pero de repente me voy al Volcán. ¿Los frenos aguantan?' },
          { id: 'g4', fromUserId: 'beto', kind: 'text', at: now - 26 * minute, text: 'Sí, los cambié hace un mes. Si quieres te la presto una tarde antes de cerrar el trato.' }
        ]
      },
      {
        id: 'x2', userA: 'me', userB: 'mari', status: 'CHAT', createdAt: now - 420 * minute,
        meeting: null, cashCents: 0, lastReadByMe: 0,
        messages: [
          { id: 'g5', fromUserId: 'system', kind: 'system', at: now - 420 * minute, text: 'Match de trueque: ambos marcaron interés. Ya pueden hablar sin compromiso.' },
          { id: 'g6', fromUserId: 'mari', kind: 'text', at: now - 380 * minute, text: 'Hola. Me interesa la bocina para los ensayos de Nutrición. La blusa es talla M y la conserva la hice esta semana.' }
        ]
      },
      {
        id: 'x3', userA: 'me', userB: 'fer', status: 'CERRADO', createdAt: now - 3280 * minute,
        meeting: 'Explanada de la facultad', cashCents: 0, lastReadByMe: now - 3000 * minute,
        messages: [
          { id: 'g7', fromUserId: 'system', kind: 'system', at: now - 3280 * minute, text: 'Match de trueque: ambos marcaron interés. Ya pueden hablar sin compromiso.' },
          { id: 'g8', fromUserId: 'fer', kind: 'text', at: now - 3200 * minute, text: 'Te cambio la guitarra por la bocina, si te late. Ya la probé, suena bien.' },
          { id: 'g9', fromUserId: 'me', kind: 'text', at: now - 3100 * minute, text: 'Va. Nos vemos en la explanada el jueves.' },
          { id: 'g10', fromUserId: 'system', kind: 'system', at: now - 3000 * minute, text: 'Trueque marcado como intercambiado en Explanada de la facultad.' }
        ]
      }
    ];

    return { version: 3, me: 'me', users: users, listings: listings, interests: interests, matches: matches };
  }

  // ---------------------------------------------------------------------------------------------
  // Estado
  // ---------------------------------------------------------------------------------------------

  function load() {
    try {
      var raw = window.localStorage.getItem(STORE_KEY);
      if (raw) {
        var parsed = JSON.parse(raw);
        if (parsed && parsed.version === 3 && parsed.users && parsed.listings &&
            parsed.interests && parsed.matches) {
          return parsed;
        }
      }
    } catch (error) {
      // datos corruptos: se vuelve a sembrar el bazar
    }
    for (var i = 0; i < LEGACY_KEYS.length; i++) {
      try { window.localStorage.removeItem(LEGACY_KEYS[i]); } catch (error) { /* sin storage */ }
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
    tradesTab: 'matches',
    giveId: null,
    getId: null,
    cash: '',
    chatDraft: '',
    editingProfile: false,
    confirmRetire: null,
    form: freshForm()
  };

  // Pila de navegación: "Ver perfil" desde un match regresa al match, no al inicio.
  var history = [];

  function freshForm() {
    return {
      title: '',
      description: '',
      emoji: '📦',
      category: 'CRAFTS',
      condition: 'GOOD',
      ageMonths: 0,
      reference: '',
      asking: '',
      complete: true,
      hasReceipt: false,
      barter: false
    };
  }

  function fallbackUser(id) {
    return { id: id, name: 'Compañero', phone: '', town: 'Colima', program: 'Alumnado', semester: '', rating: 5, trades: 0, since: '2025' };
  }

  function me() {
    return data.users[data.me] || fallbackUser(data.me);
  }

  function user(id) {
    return data.users[id] || fallbackUser(id);
  }

  function listing(id) {
    for (var i = 0; i < data.listings.length; i++) if (data.listings[i].id === id) return data.listings[i];
    return null;
  }

  function match(id) {
    for (var i = 0; i < data.matches.length; i++) if (data.matches[i].id === id) return data.matches[i];
    return null;
  }

  // ---------------------------------------------------------------------------------------------
  // Reglas del match: interés privado, chat sólo con reciprocidad
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

  /** Objetos míos (abiertos a trueque) que esta persona marcó: lo que quiere de mí. */
  function wantedFromMe(otherId) {
    var items = [];
    data.interests.forEach(function (mark) {
      var item = listing(mark.listingId);
      if (mark.fromUserId === otherId && item && item.ownerId === data.me && item.barter) {
        items.push(item);
      }
    });
    return uniq(items);
  }

  /** Objetos suyos que yo marqué: lo que quiero de él/ella. */
  function wantedByMe(otherId) {
    var items = [];
    data.interests.forEach(function (mark) {
      var item = listing(mark.listingId);
      if (mark.fromUserId === data.me && item && item.ownerId === otherId && item.barter) {
        items.push(item);
      }
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

  /** Crea los matches que el interés mutuo ya permite. Avisa a Avisos sólo en ese momento. */
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

  function myBarterables() {
    return data.listings.filter(function (item) {
      return item.ownerId === data.me && item.barter && item.status === 'DISPONIBLE';
    });
  }

  function lastMessageAt(matchItem) {
    var last = 0;
    matchItem.messages.forEach(function (message) { if (message.at > last) last = message.at; });
    return last;
  }

  function hasTheirMessage(matchItem) {
    return matchItem.messages.some(function (message) {
      return message.kind === 'text' && message.fromUserId !== data.me;
    });
  }

  function pendingCount() {
    return data.matches.filter(function (item) {
      if (item.status !== 'CHAT') return false;
      if (!hasTheirMessage(item)) return false;
      return (item.lastReadByMe || 0) < lastMessageAt(item);
    }).length;
  }

  function myInterestMarks() {
    return data.interests.filter(function (mark) {
      var item = listing(mark.listingId);
      return mark.fromUserId === data.me && item && item.ownerId !== data.me;
    });
  }

  /** Lo que otros marcaron de mis objetos: sepan cuántos, nunca quiénes (hasta el match). */
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
    var hidden = ids.filter(function (id) { return !matchWith(id); }).length;
    return { people: ids.length, items: Object.keys(items).length, hidden: hidden };
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
    }, 2600);
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

  function interestButton(item) {
    var marked = haveInterest(item.id);
    return '<button class="btn ' + (marked ? 'green' : 'rose') + ' sm" data-act="interest" data-id="' + item.id + '">' +
      (marked ? '✓ Te interesa' : '♡ Me interesa') + '</button>';
  }

  function card(item) {
    var value = appraise(item);
    var owner = user(item.ownerId);
    var mine = item.ownerId === data.me;
    var canInterest = item.barter && !mine && item.status === 'DISPONIBLE';
    var matched = canInterest && isMutual(owner.id);
    var statusBadge = '';
    if (item.status === 'RESERVADO') statusBadge = '<span class="badge badge-done">Apartado</span>';
    if (item.status === 'TRUEQUEADO') statusBadge = '<span class="badge badge-done">Truequeado</span>';
    if (item.status === 'VENDIDO') statusBadge = '<span class="badge badge-done">Vendido</span>';

    return '<article class="item-card" data-act="open" data-id="' + item.id + '">' +
      '<div class="thumb">' + item.emoji + (item.barter ? '<span class="flag">Trueque</span>' : '') + '</div>' +
      '<div class="item-body">' +
        '<h3>' + esc(item.title) + '</h3>' +
        '<div class="item-meta">' + esc(conditionById(item.condition).short) + ' · ' + esc(owner.name) +
          ' · ' + esc(owner.town) + '</div>' +
        '<div class="item-values">' +
          '<div class="ask">' + money(item.askingCents) + '<small>pide</small></div>' +
          '<div class="est">' + money(value.estimatedCents) + '<small>valor estimado</small></div>' +
        '</div>' +
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
    var barterCount = data.listings.filter(function (item) {
      return item.barter && item.status === 'DISPONIBLE';
    }).length;
    var matches = data.matches.filter(function (item) {
      return item.status === 'CHAT' || item.status === 'ACORDADO';
    }).length;

    return '<section class="hero">' +
      '<p class="kicker">Comunidad estudiantil · Colima</p>' +
      '<h1>Compra, vende y <em>truequea</em> con quienes sí quieren</h1>' +
      '<p class="lede">Publica lo que ya no usas y marca lo que sí te interesa. El chat se abre ' +
        'sólo cuando el interés es de ida y vuelta: sin compromisos ni presión.</p>' +
      '<div class="hero-stats">' +
        stat(data.listings.filter(function (item) { return item.status === 'DISPONIBLE'; }).length, 'objetos') +
        stat(barterCount, 'en trueque') +
        stat(matches, matches === 1 ? 'match' : 'matches') +
      '</div>' +
    '</section>' +
    '<div class="search"><span>⌕</span><input id="q" type="search" placeholder="Busca objeto, comunidad o persona" value="' + esc(ui.query) + '"></div>' +
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

  function itemView(id) {
    var item = listing(id);
    if (!item) return notFound('Ese objeto ya no está en el bazar.');
    var value = appraise(item);
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
        'Pueden hablar y cerrar el trato con la valoración a la vista.</p>' +
      '<button class="btn green block" data-act="open-match" data-id="' + owner.id + '">Abrir chat de trueque</button>' +
      '</div>' : '';

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
        ? '<div class="hint-line" style="margin-top:10px">Trueque con match: ' + esc(owner.name) +
          ' no ve tu interés hasta que ambos marquen algo del otro.</div>'
        : '<div class="note">Este objeto no acepta trueque, pero puedes comprarlo con tu cartera Nahua.</div>') +
      mutualBlock +
      '<div class="actions-block">' + actions + '</div>' +
      '<div style="margin-top:14px">' + valuationPanel(value) + '</div>' +
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
            '<div class="item-meta">≈ ' + money(appraise(item).estimatedCents) + ' de valor · ' +
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
        '<div class="swap-item"><span class="lbl">Objeto</span><span class="e">' + item.emoji + '</span>' +
          '<b>' + esc(categoryById(item.category).label) + '</b><small>≈ ' + money(appraise(item).estimatedCents) + '</small></div>' +
        '<div class="swap-arrow">' + (mutual ? '⇄' : '…') + '</div>' +
        '<div class="swap-item"><span class="lbl">Estado</span><span class="e">' + (mutual ? '✓' : '⏳') + '</span>' +
          '<b>' + (mutual ? 'Match: ya hay chat' : 'Esperando reciprocidad') + '</b>' +
          '<small>' + esc(owner.town) + '</small></div>' +
      '</div>' +
      '<div class="offer-foot"><span class="pill ' + (mutual ? 'aceptado' : 'pendiente') + '">' +
        (mutual ? 'Chat abierto' : 'Interés privado') + '</span></div>' +
    '</article>';
  }

  function verdictPanel(balance) {
    var icon = balance.verdict === 'favorable' ? '✓' : (balance.verdict === 'desfavorable' ? '!' : '⇄');
    return '<div class="verdict ' + balance.verdict + '">' +
      '<span class="vd-icon">' + icon + '</span>' +
      '<div><strong>' + esc(balance.verdictLabel) + '</strong><p>' + esc(balance.message) + '</p></div>' +
    '</div>';
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
    var balance = pair.give && pair.get ? balanceOf(pair.give, pair.get) : null;

    var chat = item.messages.map(function (message) {
      if (message.kind === 'system') {
        return '<div class="chat-sys">' + esc(message.text) + '</div>';
      }
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
      '<p class="detail-meta">' + esc(other.town) + ' · ' + esc(other.program) + ' · ' +
        esc(other.semester) + '</p>' +
      contactRow(other.id) +
      '<div class="section-head"><h2>Interés mutuo</h2><span>' + pair.mine.length + ' ⇄ ' + pair.theirs.length + '</span></div>' +
      (open
        ? '<div class="field"><label>Das</label>' + pairOptions(pair.mine, 'pick-give', pair.give) + '</div>' +
          '<div class="field"><label>Recibes</label>' + pairOptions(pair.theirs, 'pick-get', pair.get) + '</div>'
        : '<div class="deal-grid">' +
            '<div class="deal-box"><span class="l">Das</span><span class="e">' + (pair.give ? pair.give.emoji : '📦') + '</span>' +
              '<span class="n">' + esc(pair.give ? pair.give.title : '—') + '</span>' +
              '<span class="v tabular">' + money(pair.give ? appraise(pair.give).estimatedCents : 0) + '</span></div>' +
            '<div class="deal-vs">⇄</div>' +
            '<div class="deal-box"><span class="l">Recibes</span><span class="e">' + (pair.get ? pair.get.emoji : '📦') + '</span>' +
              '<span class="n">' + esc(pair.get ? pair.get.title : '—') + '</span>' +
              '<span class="v tabular">' + money(pair.get ? appraise(pair.get).estimatedCents : 0) + '</span></div>' +
          '</div>') +
      (balance ? verdictPanel(balance) : '') +
      (pair.give && pair.get
        ? '<div class="value-pair">' +
            '<div><span>Das · valor</span><b>' + money(appraise(pair.give).estimatedCents) + '</b>' +
              '<small>mínimo trueque ' + money(appraise(pair.give).barterMinCents) + '</small></div>' +
            '<div><span>Recibes · valor</span><b>' + money(appraise(pair.get).estimatedCents) + '</b>' +
              '<small>mínimo trueque ' + money(appraise(pair.get).barterMinCents) + '</small></div>' +
          '</div>'
        : '') +
      (item.cashCents > 0 ? '<div class="panel" style="margin-top:10px;background:var(--gold-soft);border-color:#E6C58A">' +
        '<div class="panel-head"><h3>Compensación en efectivo</h3></div>' +
        '<div class="value-big tabular">' + money(item.cashCents) + '</div>' +
        '<div class="value-range">Acordada en el chat para emparejar el valor.</div></div>' : '') +
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
        '<div class="field"><label>Compensación (opcional)</label>' +
          '<input id="cashInput" type="number" inputmode="numeric" placeholder="Ej. 250" value="' + esc(ui.cash) + '">' +
          '<div class="help">Si la diferencia de valor lo pide' +
            (balance && balance.cashTopUpCents > 0 ? ', el sistema sugiere ' + money(balance.cashTopUpCents) : '') +
          '. Pesos, sin centavos.</div></div>' +
        '<button class="btn green block" data-act="agree" data-id="' + item.id + '">✓ Acordar trueque</button>' +
        '<button class="btn danger block" data-act="archive" data-id="' + item.id + '">Retirar mi interés</button>';
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

    body += '</div>';
    return body;
  }

  function profileView(id) {
    var profile = user(id);
    var mine = id === data.me;
    var items = activeListingsFor(id);
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
        (mine ? '<button class="btn block" style="margin-top:8px" data-act="edit-profile">Editar nombre y teléfono</button>' : '');

    var cards = items.length
      ? items.map(function (item) {
          var value = appraise(item);
          var canInterest = !mine && item.barter && item.status === 'DISPONIBLE';
          return '<article class="item-card" data-act="open" data-id="' + item.id + '">' +
            '<div class="thumb">' + item.emoji + (item.barter ? '<span class="flag">Trueque</span>' : '') + '</div>' +
            '<div class="item-body"><h3>' + esc(item.title) + '</h3>' +
              '<div class="item-meta">' + esc(conditionById(item.condition).short) + ' · ≈ ' + money(value.estimatedCents) + ' de valor</div>' +
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
        ? '<div class="note">Así te ven los demás. Tu teléfono no aparece en el bazar: sólo en un chat con match.</div>'
        : '') +
      '<div class="section-head"><h2>' + (mine ? 'Mi bazar' : 'Su bazar') + '</h2><span>' + barterables.length + ' en trueque</span></div>' +
      '<div class="cards">' + cards + '</div>';
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
      '<p class="lede">Lo valoramos para que sepas cuánto vale. El trueque con match se activa cuando tú quieras.</p>' +

      '<div class="field"><label>Título</label>' +
        '<input id="fTitle" type="text" maxlength="60" placeholder="Ej. Blusa bordada de Suchitlán" value="' + esc(form.title) + '"></div>' +

      '<div class="field"><label>Descripción</label>' +
        '<textarea id="fDesc" maxlength="240" placeholder="Estado real, qué incluye, de qué comunidad viene">' + esc(form.description) + '</textarea></div>' +

      '<div class="field"><label>Ícono</label><div class="emoji-grid">' + emojis + '</div></div>' +

      '<div class="field"><label>Categoría</label><div class="option-grid">' + categories + '</div></div>' +

      '<div class="field"><label>Estado</label><div class="option-grid">' + conditions + '</div></div>' +

      '<div class="grid-2">' +
        '<div class="field"><label>Meses de uso</label>' +
          '<input id="fAge" type="number" inputmode="numeric" min="0" max="360" value="' + esc(form.ageMonths) + '"></div>' +
        '<div class="field"><label>Precio nuevo / referencia</label>' +
          '<input id="fRef" type="number" inputmode="numeric" placeholder="Ej. 1200" value="' + esc(form.reference) + '"></div>' +
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
        '<div class="txt"><strong>Acepto trueque con match</strong>' +
          '<span>' + (value.hasValue
            ? 'Se abre chat sólo cuando haya interés de las dos partes'
            : 'Primero valora el objeto para poder activar el trueque') + '</span></div>' +
        '<button class="switch' + (form.barter ? ' on' : '') + (value.hasValue ? '' : ' disabled') +
          '" data-act="toggle-barter" aria-label="Acepto trueque"' + (value.hasValue ? '' : ' disabled') + '></button>' +
      '</div>' +
      (value.hasValue && form.barter
        ? '<div class="hint-line">Aceptarás trueques desde <b class="tabular">' + money(value.barterMinCents) +
          '</b> de valor. Tú decides a quién decir que sí: marcar interés no compromete nada.</div>'
        : '') +

      '<div class="actions-block">' +
        '<button class="btn primary block" data-act="publish">Publicar en el bazar</button>' +
      '</div>' +
      '<p class="footer-note">Tus publicaciones se guardan en este dispositivo.</p>';
  }

  function howView() {
    return backButton('Bazar', 'feed') +
      '<p class="kicker">Cómo funciona el trueque</p>' +
      '<h1 style="font-size:21px;margin-top:4px">Interés privado, chat sólo con match</h1>' +
      '<div class="panel" style="margin-top:12px">' +
        '<ol class="steps">' +
          '<li><b>Publica</b> lo que ya no usas y marca si acepta trueque. Lo demás se vende normal.</li>' +
          '<li><b>Marca «Me interesa»</b> en lo que sí quieres. Nadie recibe aviso: tu interés es privado.</li>' +
          '<li><b>Match</b>: cuando la otra persona también marca algo tuyo, el sistema abre el chat y avisa a los dos con nombres y objetos.</li>' +
          '<li><b>Valoración a la vista</b>: el chat muestra cuánto valen los dos objetos y si conviene compensar con efectivo.</li>' +
          '<li><b>Cierra el trato</b> cuando tú quieras: acuerden punto de encuentro y marquen el intercambio.</li>' +
        '</ol>' +
      '</div>' +
      '<div class="note" style="margin-top:12px">Si nadie marcó algo tuyo, no hay chat ni aviso: así nadie truequea por ' +
        'compromiso o por presión social. El contacto (nombre y teléfono) también aparece sólo con match.</div>' +
      '<div class="panel" style="margin-top:12px">' +
        '<div class="panel-head"><h3>Ejemplo</h3></div>' +
        '<p style="font-size:12px;color:var(--ink-soft);margin-top:6px">Marcas una blusa bordada y una conserva. ' +
          'La dueña, a su vez, marcó tu bocina. Eso es match: se abre el chat con «le interesan 2 de tus objetos y tú 2 de los suyos». ' +
          'Si ella no hubiera marcado nada tuyo, nunca se enteraría de tu interés.</p>' +
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
    else if (route.name === 'match') html = matchView(route.id);
    else if (route.name === 'profile') html = profileView(route.id);
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
    if (route.name === 'match') wireChat();
    if (route.name === 'profile') wireProfile();
  }

  function updateTabs() {
    var tabOf = {
      feed: 'feed', item: 'feed', how: 'feed',
      trades: 'trades', match: 'trades',
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
          ? 'Se abre chat sólo cuando haya interés de las dos partes'
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
    else if (tab === 'me') { ui.editingProfile = false; ui.route = { name: 'profile', id: data.me }; render(); }
  }

  /**
   * Marca o retira el interés. Retirar antes del match es silencioso para la otra persona.
   * Si el interés se vuelve mutuo, reconcileMatches() abre el chat y avisa en Avisos.
   */
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
      var entry = created[0];
      flash('¡Match con ' + entry.other.name + '! Ya pueden hablar');
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
      text: 'Trato acordado: ' + pair.give.title + ' ⇄ ' + pair.get.title + ' en ' + meeting +
        (item.cashCents > 0 ? ', con ' + money(item.cashCents) + ' de compensación' : '') + '.'
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

    // Retirar mi interés cierra el match pero deja el historial del chat intacto.
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
    try {
      if (window.Nahua && Nahua.copy) Nahua.copy(phone);
    } catch (error) { /* sin portapapeles del host */ }
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
    if (action === 'buy') return buyListing(id);
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
  }

  // Expuesto a propósito: el candado de pruebas del bundle verifica la regla del match.
  window.Bazar = {
    rule: 'chat-only-on-mutual-interest',
    data: function () { return data; },
    mutual: function (otherId) { return isMutual(otherId); },
    pending: function () { return pendingCount(); },
    formatPhone: formatPhone
  };

  start();
})();
