/*
 * Emulador (Nahua Arcade) — logica de la mini-app.
 * -------------------------------------------------------------
 * Tres pantallas: la portada (escenario pixel + biblioteca), el juego (EmulatorJS) y el panel
 * de ajustes. Un solo motor, dos nucleos: mgba para Game Boy Advance y gambatte para Game
 * Boy / Game Boy Color; el nucleo se elige por la extension del archivo.
 *
 * Reglas del anfitrion que esta pagina respeta:
 *  - Las ROMs del usuario nunca entran por la pagina: las elige con el selector del sistema
 *    (Nahua.pickRom), el host las copia a su almacenamiento privado y nos devuelve una URL
 *    https del propio origen. Sin file://, sin URIs de contenido.
 *  - El bundle del emulador vive en ./emulatorjs/ dentro del APK: todo offline. El anfitrion
 *    bloquea cualquier request a otro origen, asi que si falta un archivo se ve un error
 *    inmediato, no un timeout ni una fuga a cdn.emulatorjs.org.
 *  - EmulatorJS guarda partidas en IndexedDB del origen; el nombre del juego es el nombre
 *    estable de la ROM (con su sha8), asi que reimportar la misma ROM conserva las partidas.
 */
(function () {
  'use strict';

  var LAST_KEY = 'nahua.arcade.last';
  var CONTROLS_KEY = 'nahua.arcade.pad';
  var OPTIONS_KEY = 'nahua.arcade.options';
  var loaderInjected = false;

  // --- Utilidades -----------------------------------------------------------

  function byId(id) { return document.getElementById(id); }

  function hasHost() {
    return typeof window.NahuaNative === 'object' && window.NahuaNative !== null;
  }

  function hasSdk() {
    return typeof window.Nahua === 'object' && window.Nahua !== null;
  }

  function toast(message) {
    if (hasSdk() && typeof window.Nahua.toast === 'function') window.Nahua.toast(message);
  }

  function clamp(value, min, max) {
    var number = Number(value);
    if (!isFinite(number)) return min;
    return Math.min(max, Math.max(min, number));
  }

  function clampNumber(value, min, max, fallback) {
    var number = Number(value);
    if (!isFinite(number)) return fallback;
    return Math.min(max, Math.max(min, number));
  }

  function readJson(key, fallback) {
    try {
      var raw = window.localStorage.getItem(key);
      var parsed = raw ? JSON.parse(raw) : null;
      return parsed && typeof parsed === 'object' ? parsed : fallback;
    } catch (error) {
      return fallback;
    }
  }

  function writeJson(key, value) {
    try {
      window.localStorage.setItem(key, JSON.stringify(value));
    } catch (error) {
      // Sin localStorage la configuracion vive solo en esta sesion.
    }
  }

  function formatBytes(bytes) {
    var value = Number(bytes);
    if (!isFinite(value) || value <= 0) return '';
    if (value < 1024 * 1024) return (value / 1024).toFixed(0) + ' KB';
    return (value / (1024 * 1024)).toFixed(1) + ' MB';
  }

  // --- Consolas y nucleos ---------------------------------------------------
  //
  // EmulatorJS mapea EJS_core -> nucleo: "gba" -> mgba y "gb" -> gambatte. El mismo nucleo
  // "gb" emula Game Boy y Game Boy Color (gambatte detecta el modo por la cabecera del
  // cartucho), asi que .gbc y .gb comparten consola y controles.

  var CONSOLES = {
    gba: { core: 'gba', label: 'GBA', title: 'Game Boy Advance' },
    gb: { core: 'gb', label: 'GBC', title: 'Game Boy / Color' }
  };

  function extensionOf(name) {
    var text = String(name || '');
    var index = text.lastIndexOf('.');
    return index === -1 ? '' : text.slice(index + 1).toLowerCase();
  }

  function consoleFor(name) {
    var extension = extensionOf(name);
    // .gb y .gbc comparten nucleo (gambatte detecta el modo por la cabecera del cartucho).
    if (extension === 'gbc' || extension === 'gb') return CONSOLES.gb;
    return CONSOLES.gba;
  }

  // --- Juego incluido en el APK --------------------------------------------

  var BUILTIN = [
    {
      id: 'smbdx',
      name: 'smbdx',
      displayName: 'Super Mario Bros. Deluxe',
      url: 'games/smbdx.gbc',
      sizeBytes: 1048576,
      core: 'gb',
      consoleLabel: 'GBC',
      included: true
    }
  ];

  // --- Escenario pixel animado ---------------------------------------------
  //
  // Un mundo de 176x104 pixeles logicos dibujado a mano (sin imagenes, sin sprites de terceros)
  // que se escala por enteros: cada pixel logico cae en un bloque exacto de pixeles de pantalla,
  // asi el look de 8 bits no se ve borroso. Se detiene solo cuando la pagina se oculta o cuando
  // se entra al juego, para no gastar bateria.

  var Scene = (function () {
    var canvas = byId('scene');
    var ctx = canvas && canvas.getContext ? canvas.getContext('2d') : null;
    var W = 176;
    var H = 104;
    var px = 4;
    var running = false;
    var raf = 0;
    var last = 0;
    var clock = 0;
    var boostedAt = -99;
    var reduced = false;

    var C = {
      sky1: '#0C1E31',
      sky2: '#14374F',
      sky3: '#1F5573',
      sky4: '#2C6F8C',
      star: '#CFE6F5',
      moon: '#F4E9C9',
      moonShade: '#D9C79E',
      cloud: '#2A5A78',
      cloudLight: '#3B7290',
      hillFar: '#123A46',
      hillNear: '#155043',
      grass: '#2F7D5B',
      grassDark: '#1D5540',
      dirt: '#3A2C22',
      dirtLine: '#4A3A2C',
      block: '#C98A3C',
      blockDark: '#8A5518',
      blockInk: '#3A2208',
      gold: '#F5C542',
      goldDark: '#B08A1B',
      pipe: '#2F7D5B',
      pipeDark: '#1B5741',
      pipeLight: '#3E9A72',
      body: '#F2A0B8',
      bodyDark: '#D97C9C',
      gill: '#C9557F',
      eye: '#141F28',
      cap: '#E2603C',
      capDark: '#A93F24',
      capLight: '#FFF6E5'
    };

    /** Rectangulo en pixeles logicos. */
    function r(x, y, w, h, color) {
      ctx.fillStyle = color;
      ctx.fillRect(Math.round(x) * px, Math.round(y) * px, Math.round(w) * px, Math.round(h) * px);
    }

    function resize() {
      if (!ctx) return;
      var box = canvas.getBoundingClientRect();
      // Con la portada oculta (juego abierto) el canvas mide 0: no se toca, o al girar el
      // telefono quedaria dibujado a 1 px para siempre.
      if (box.width < 40) return;
      var dpr = Math.min(window.devicePixelRatio || 1, 2);
      // Escala entera: si el ancho real no es multiplo exacto, el resto lo cubre el CSS.
      px = Math.max(1, Math.round((box.width * dpr) / W));
      canvas.width = W * px;
      canvas.height = H * px;
      ctx.imageSmoothingEnabled = false;
      draw(clock);
    }

    function stars(t) {
      var i;
      for (i = 0; i < 22; i++) {
        var sx = (i * 37) % W;
        var sy = 4 + ((i * 13) % 30);
        // Parpadeo lento y distinto por estrella: dos senos desfasados por indice.
        var alpha = 0.35 + 0.45 * Math.abs(Math.sin(t * 0.9 + i));
        ctx.globalAlpha = alpha;
        r(sx, sy, 1, 1, C.star);
      }
      ctx.globalAlpha = 1;
    }

    function moon() {
      var mx = 138;
      var my = 14;
      r(mx + 2, my, 5, 1, C.moon);
      r(mx, my + 2, 9, 5, C.moon);
      r(mx + 2, my + 7, 5, 1, C.moon);
      r(mx + 5, my + 2, 2, 2, C.moonShade);
      r(mx + 2, my + 4, 2, 1, C.moonShade);
    }

    function clouds(sx) {
      var period = 118;
      var i;
      for (i = 0; i < 2; i++) {
        var baseX = i * period + 14 - ((sx * 0.25) % period);
        var y = 26 + (i % 2) * 12;
        // Nube de bloques: 5 rectangulos escalonados.
        r(baseX, y + 3, 22, 4, C.cloud);
        r(baseX + 3, y, 16, 4, C.cloud);
        r(baseX + 6, y - 3, 9, 4, C.cloud);
        r(baseX + 3, y, 16, 1, C.cloudLight);
        r(baseX + 6, y - 3, 9, 1, C.cloudLight);
      }
    }

    function hills(sx) {
      var period = 132;
      var i;
      for (i = 0; i < 2; i++) {
        var x = i * period + 10 - ((sx * 0.45) % period);
        // Colina lejana.
        r(x + 10, 62, 26, 8, C.hillFar);
        r(x + 14, 58, 18, 6, C.hillFar);
        r(x + 18, 55, 10, 4, C.hillFar);
        // Colina cercana, mas alta y verde.
        r(x + 52, 58, 34, 12, C.hillNear);
        r(x + 58, 54, 22, 6, C.hillNear);
        r(x + 64, 51, 10, 4, C.hillNear);
      }
    }

    function ground(sx) {
      // Cesped arriba, tierra con ladrillos abajo; el patron se repite cada 8 px.
      r(0, 74, W, 3, C.grass);
      r(0, 77, W, 2, C.grassDark);
      r(0, 79, W, H - 79, C.dirt);
      var offset = Math.round(sx) % 8;
      var x;
      for (x = -8; x < W + 8; x += 8) {
        r(x - offset, 82, 1, 18, C.dirtLine);
        r(x - offset + 3, 86, 1, 12, C.dirtLine);
        r(x - offset + 6, 92, 1, 8, C.dirtLine);
      }
      // Linea de hongos del borde: puntitos claros cada 8 px.
      for (x = -8; x < W + 8; x += 8) {
        r(x - offset + 2, 74, 3, 1, '#4A9B72');
      }
    }

    function blocks(sx) {
      var period = 176;
      var baseX = 104 - (sx % period);
      var i;
      for (i = 0; i < 2; i++) {
        var x = baseX + i * 20;
        r(x, 60, 14, 14, C.block);
        r(x, 60, 14, 2, '#E4AE5E');
        r(x, 71, 14, 3, C.blockDark);
        r(x, 60, 2, 14, '#E4AE5E');
        r(x + 12, 60, 2, 14, C.blockDark);
        // Signo de interrogacion en 4x6 pixeles.
        r(x + 5, 63, 5, 1, C.blockInk);
        r(x + 9, 64, 1, 2, C.blockInk);
        r(x + 6, 66, 3, 1, C.blockInk);
        r(x + 6, 68, 1, 1, C.blockInk);
        r(x + 6, 70, 1, 1, C.blockInk);
        // Remaches.
        r(x + 1, 61, 1, 1, C.blockDark);
        r(x + 12, 61, 1, 1, C.blockDark);
      }
    }

    function coins(sx, t) {
      var period = 176;
      var baseX = 122 - (sx % period);
      var i;
      for (i = 0; i < 3; i++) {
        var x = baseX + i * 9;
        var phase = Math.abs(Math.cos(t * 3 + i * 0.5));
        var half = Math.max(1, Math.round(3 * phase));
        // Moneda girando: se aplasta en el eje X.
        r(x - half, 44, half * 2, 7, C.gold);
        r(x - half, 44, half * 2, 1, '#FFE08A');
        r(x - half, 50, half * 2, 1, C.goldDark);
        if (phase > 0.55) r(x - 1, 46, 1, 3, C.goldDark);
      }
    }

    function pipe(sx) {
      var period = 176;
      var baseX = 152 - (sx % period);
      // Tuberia de 18 px de ancho con reborde.
      r(baseX, 50, 18, 4, C.pipeLight);
      r(baseX, 54, 18, 20, C.pipe);
      r(baseX + 2, 54, 4, 20, C.pipeLight);
      r(baseX + 13, 54, 5, 20, C.pipeDark);
      r(baseX + 1, 50, 2, 4, C.pipeLight);
      r(baseX + 15, 50, 3, 4, C.pipeDark);
    }

    /** El axolote de la casa: cuerpo rosa, branquias coral y gorra roja. */
    function hero(x, y, t, jump) {
      var legPhase = Math.floor(t * 9) % 2;
      var lift = legPhase === 0 ? 0 : 1;
      // Sombra en el suelo (queda fija aunque salte).
      ctx.globalAlpha = 0.35;
      r(x + 1, 73, 11, 1, '#0A1418');
      ctx.globalAlpha = 1;

      var top = y;
      // Branquias (tres por lado).
      r(x - 2, top + 1, 2, 1, C.gill);
      r(x - 3, top + 3, 2, 1, C.gill);
      r(x - 2, top + 5, 2, 1, C.gill);
      r(x + 10, top + 1, 2, 1, C.gill);
      r(x + 11, top + 3, 2, 1, C.gill);
      r(x + 10, top + 5, 2, 1, C.gill);
      // Cuerpo.
      r(x, top + 1, 10, 7, C.body);
      r(x, top + 7, 10, 1, C.bodyDark);
      r(x, top + 1, 10, 1, '#FBC0D2');
      // Cara.
      r(x + 6, top + 2, 3, 3, '#FBC0D2');
      r(x + 7, top + 3, 1, 1, C.eye);
      r(x + 8, top + 3, 1, 1, C.eye);
      r(x + 7, top + 5, 2, 1, '#B85B7B');
      // Gorra.
      r(x + 3, top - 1, 6, 2, C.cap);
      r(x + 2, top + 1, 8, 1, C.cap);
      r(x + 4, top - 1, 3, 1, C.capLight);
      r(x + 2, top + 1, 2, 1, C.capDark);
      // Patas.
      r(x + 1, top + 8, 2, 1 + lift, C.bodyDark);
      r(x + 6, top + 8, 2, 2 - lift, C.bodyDark);
      // Cola.
      r(x - 1, top + 4, 1, 3, C.bodyDark);
    }

    function draw(t) {
      if (!ctx) return;
      var sx = t * 34;

      r(0, 0, W, 40, C.sky1);
      r(0, 40, W, 20, C.sky2);
      r(0, 60, W, 10, C.sky3);
      r(0, 70, W, 4, C.sky4);

      stars(t);
      moon();
      clouds(sx);
      hills(sx);
      pipe(sx);
      blocks(sx);
      coins(sx, t);
      ground(sx);

      // Salto: parabola lenta cada 6 s, mas un empujon si el usuario toca el escenario.
      var cycle = t % 6;
      var jump = cycle < 1.1 ? Math.sin((cycle / 1.1) * Math.PI) * 16 : 0;
      var boost = Math.max(0, 1 - (t - boostedAt) / 0.7);
      jump += boost * boost * 22;
      var bob = Math.round(Math.abs(Math.sin(t * 6)) * 1);
      hero(46, 64 - Math.round(jump) - bob, t, jump);
    }

    function loop(ts) {
      if (!running) return;
      raf = window.requestAnimationFrame(loop);
      // 24 fps: suficiente para que se sienta vivo y no gasta bateria ni CPU del juego.
      if (ts - last < 41) return;
      last = ts;
      clock += 0.041;
      draw(clock);
    }

    function stopLoop() {
      running = false;
      if (raf) window.cancelAnimationFrame(raf);
      raf = 0;
    }

    function start() {
      if (!ctx || running) return;
      try {
        reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      } catch (error) {
        reduced = false;
      }
      if (reduced || document.hidden) {
        draw(clock);
        return;
      }
      running = true;
      last = 0;
      raf = window.requestAnimationFrame(loop);
    }

    function pause() { stopLoop(); }

    if (canvas) {
      canvas.addEventListener('pointerdown', function () {
        boostedAt = clock;
      });
      document.addEventListener('visibilitychange', function () {
        if (document.hidden) stopLoop();
        else if (!byId('home').classList.contains('hidden')) start();
      });
      window.addEventListener('resize', function () { resize(); });
    }

    return {
      start: start,
      pause: pause,
      resize: function () { resize(); },
      redraw: function () { draw(clock); }
    };
  })();

  // --- Portada: seguir jugando ---------------------------------------------

  var continueCard = byId('continueCard');
  var continueName = byId('continueName');
  var continueMeta = byId('continueMeta');
  var continueBtn = byId('continueBtn');

  function readLast() {
    var last = readJson(LAST_KEY, null);
    return last && last.url ? last : null;
  }

  function renderContinue() {
    var last = readLast();
    if (!last) {
      continueCard.classList.add('hidden');
      return;
    }
    continueName.textContent = last.displayName || last.name || 'Tu ROM';
    var console_ = CONSOLES[last.core === 'gb' ? 'gb' : 'gba'];
    continueMeta.textContent = (last.consoleLabel || console_.label) + ' · ' + (formatBytes(last.sizeBytes) || 'listo');
    continueCard.classList.remove('hidden');
  }

  // --- Biblioteca -----------------------------------------------------------

  var romList = byId('romList');
  var romEmpty = byId('romEmpty');
  var romCount = byId('romCount');
  var builtinList = byId('builtinList');

  function romRow(game, options) {
    var row = document.createElement('div');
    row.className = 'rom';

    var info = document.createElement('div');
    info.className = 'rom-info';

    var name = document.createElement('p');
    name.className = 'rom-name';
    name.textContent = game.displayName || game.name;

    var meta = document.createElement('p');
    meta.className = 'rom-meta';
    var console_ = CONSOLES[game.core === 'gb' ? 'gb' : 'gba'];
    meta.textContent = [game.consoleLabel || console_.label, options.extra, formatBytes(game.sizeBytes)]
      .filter(function (part) { return !!part; })
      .join(' · ');

    info.appendChild(name);
    info.appendChild(meta);

    var play = document.createElement('button');
    play.type = 'button';
    play.className = 'btn primary small';
    play.textContent = 'Jugar';
    play.addEventListener('click', function () { startGame(game); });

    row.appendChild(info);
    row.appendChild(play);

    if (!options.included) {
      // window.confirm no funciona en WebView sin onJsConfirm: doble toque en su lugar.
      var del = document.createElement('button');
      del.type = 'button';
      del.className = 'btn danger small';
      del.textContent = 'Borrar';
      var armed = false;
      del.addEventListener('click', function () {
        if (!armed) {
          armed = true;
          del.textContent = '¿Seguro?';
          window.setTimeout(function () {
            armed = false;
            del.textContent = 'Borrar';
          }, 2600);
          return;
        }
        if (!hasSdk()) return;
        window.Nahua.deleteRom(game.name).then(refreshRoms).catch(function (error) {
          if (error && error.error === 'not_found') refreshRoms();
        });
      });
      row.appendChild(del);
    }

    return row;
  }

  function renderBuiltin() {
    BUILTIN.forEach(function (game) {
      builtinList.appendChild(romRow(game, { included: true, extra: 'incluido' }));
    });
  }

  function renderRoms(roms) {
    var rows = romList.querySelectorAll('.rom');
    var index;
    for (index = 0; index < rows.length; index++) rows[index].remove();

    var list = Array.isArray(roms) ? roms : [];
    romEmpty.classList.toggle('hidden', list.length > 0);
    romCount.textContent = String(list.length);

    list.forEach(function (rom) {
      var console_ = consoleFor(rom.name);
      romList.appendChild(romRow({
        name: rom.name,
        displayName: rom.displayName || rom.name,
        url: rom.url,
        sizeBytes: rom.sizeBytes,
        core: console_.core,
        consoleLabel: console_.label
      }, { included: false }));
    });

    // Si la ROM "continuar" fue borrada, retirar la tarjeta. El juego incluido no vive en la
    // lista de ROMs del usuario, asi que su tarjeta se conserva.
    var last = readLast();
    if (last && !last.included &&
        (list.length === 0 || !list.some(function (rom) { return rom.name === last.name; }))) {
      window.localStorage.removeItem(LAST_KEY);
      renderContinue();
    }
  }

  function refreshRoms() {
    if (!hasSdk() || typeof window.Nahua.listRoms !== 'function') return;
    window.Nahua.listRoms()
      .then(function (result) { renderRoms(result.roms); })
      .catch(function () {
        // El anfitrion v1 no trae las funciones de ROMs: avisar sin romper la portada.
        byId('hint').classList.remove('hidden');
      });
  }

  // --- Ajustes: mando -------------------------------------------------------

  // EmulatorJS trae una cruceta fija y CSS con tamanos fijos: al girar el telefono el mando se
  // queda con la geometria de la orientacion anterior (su handleResize no toca el gamepad). Aqui
  // se reemplaza por un joystick ("zone" + joystickInput, el mismo mecanismo del stick del N64)
  // y un layout propio en variables CSS que se RECALCULA al girar: el lado corto de la pantalla
  // manda en el tamano, asi el mando siempre cabe en vertical y en horizontal.
  var PAD_DEFAULTS = {
    pad: 'joystick',
    scale: 1,
    bottom: 50,
    side: 10,
    hand: 'right',
    haptic: 'on',
    sprint: 'on'
  };

  function readPad() {
    var stored = readJson(CONTROLS_KEY, {});
    var config = {};
    var key;
    for (key in PAD_DEFAULTS) {
      if (Object.prototype.hasOwnProperty.call(PAD_DEFAULTS, key)) config[key] = PAD_DEFAULTS[key];
    }
    if (stored.pad === 'joystick' || stored.pad === 'dpad') config.pad = stored.pad;
    if (stored.hand === 'left' || stored.hand === 'right') config.hand = stored.hand;
    if (stored.haptic === 'on' || stored.haptic === 'off') config.haptic = stored.haptic;
    if (stored.sprint === 'on' || stored.sprint === 'off') config.sprint = stored.sprint;
    config.scale = clampNumber(stored.scale, 0.7, 1.5, config.scale);
    config.bottom = clampNumber(stored.bottom, 0, 240, config.bottom);
    config.side = clampNumber(stored.side, 0, 140, config.side);
    return config;
  }

  function savePad(config) { writeJson(CONTROLS_KEY, config); }

  /** Escala del mando segun el lado corto de la pantalla (no px fijos). */
  function padScale(config) {
    var width = window.innerWidth || 360;
    var height = window.innerHeight || 640;
    var short = Math.min(width, height);
    var landscape = width > height;
    var base = landscape ? short * 0.46 : short * 0.36;
    base = Math.max(104, Math.min(landscape ? 176 : 132, base));
    return Math.max(0.55, Math.min(2, Math.round(((base / 125) * config.scale) * 100) / 100));
  }

  function ensurePadStyle() {
    var style = byId('nahua-pad-style');
    if (!style) {
      style = document.createElement('style');
      style.id = 'nahua-pad-style';
      document.head.appendChild(style);
    }
    return style;
  }

  /** Layout del gamepad: se aplica en vivo y se repite en cada cambio de tamano. */
  function applyPadLayout(config) {
    var style = ensurePadStyle();
    var scale = padScale(config);
    var side = Math.round(config.side);
    var bottom = 'calc(' + Math.round(config.bottom) + 'px + env(safe-area-inset-bottom, 0px))';
    var base = 'calc(' + Math.round(config.bottom * 0.62) + 'px + env(safe-area-inset-bottom, 0px))';
    // Alto real del mando izquierdo ya escalado: el gesto de correr se apoya justo encima del
    // bloque de A/B para que el pulgar derecho no tenga que viajar.
    var padBox = Math.round(125 * scale);
    var sprintBottom = 'calc(' + (Math.round(config.bottom) + padBox + 8) + 'px + env(safe-area-inset-bottom, 0px))';
    // Zurdo: los mandos cambian de lado aqui, sin tocar el toggle de EmulatorJS (que mueve las
    // clases y se llevaria consigo el pad propio).
    var leftSide = config.hand === 'left' ? 'right' : 'left';
    var rightSide = config.hand === 'left' ? 'left' : 'right';
    var leftOrigin = config.hand === 'left' ? 'bottom right' : 'bottom left';
    var rightOrigin = config.hand === 'left' ? 'bottom left' : 'bottom right';
    style.textContent = [
      // El padre de EmulatorJS viene con bottom:50px fijo; se anula para que la "Altura"
      // del panel sea la distancia real al borde de la pantalla.
      '.ejs_virtualGamepad_parent{bottom:0 !important;}',
      // El mando direccional (pad propio) crece desde su esquina inferior.
      '.ejs_virtualGamepad_left{transform:scale(' + scale + ');transform-origin:' + leftOrigin + ';',
      leftSide + ':' + side + 'px !important;right:auto !important;bottom:' + bottom + ' !important;}',
      // Botones A/B desde la esquina inferior opuesta.
      '.ejs_virtualGamepad_right{transform:scale(' + scale + ');transform-origin:' + rightOrigin + ';',
      rightSide + ':' + side + 'px !important;left:auto !important;bottom:' + bottom + ' !important;}',
      // Start/Select centrados, un poco mas arriba que los mandos para no chocar.
      '.ejs_virtualGamepad_bottom{transform:scale(' + scale + ');transform-origin:bottom center;',
      'bottom:' + base + ' !important;}',
      // Correr + saltar: encima del bloque de botones, del mismo lado que A/B.
      '.nahua-sprint{' + rightSide + ':' + side + 'px !important;left:auto !important;bottom:' + sprintBottom + ' !important;}'
    ].join('');
  }

  /**
   * Mando direccional propio (joystick o cruceta).
   *
   * Por que no el de EmulatorJS: su gamepad se dibuja con px fijos y calcula el centro del area
   * tactil con clientWidth/clientHeight, que NO contemplan el `transform: scale()` con el que este
   * layout ajusta el tamano. Con el mando escalado, el eje se desfasa y "arriba" cae donde el dedo
   * espera "derecha" (justo el sintoma reportado en Super Mario Bros. Deluxe, donde avanzar es
   * derecha).
   *
   * Aqui se dibuja el mando y se envian los botones del pad de RetroArch directamente con
   * simulateInput: 4 arriba, 5 abajo, 6 izquierda, 7 derecha (los mismos ids que usa el dpad de
   * EmulatorJS). El centro se mide con getBoundingClientRect, que SI incluye el transform, asi que
   * cada direccion cae donde el dedo la empuja en cualquier tamano y en cualquier orientacion.
   */
  var NahuaPad = (function () {
    'use strict';

    var UP = 4;
    var DOWN = 5;
    var LEFT = 6;
    var RIGHT = 7;
    // Botones de accion del pad de RetroArch: 8 = A, 0 = B (los mismos que usa EmulatorJS).
    var BUTTON_A = 8;
    var BUTTON_B = 0;
    // Zona muerta: 32 % del radio. Mas chica, el pulgar tiembla y el personaje se mueve solo;
    // mas grande y hay que arrastrar demasiado para arrancar la caminata.
    var DEAD = 0.32;

    var root = null;
    var knob = null;
    var mode = 'joystick';
    var active = {};
    var pointer = null;
    var timer = 0;
    var sprintEl = null;
    var sprintKnob = null;
    var sprintPointer = null;
    var sprintRunning = false;
    var sprintJumping = false;

    function manager() {
      var emulator = window.EJS_emulator;
      if (!emulator || !emulator.gameManager) return null;
      var gm = emulator.gameManager;
      return typeof gm.simulateInput === 'function' ? gm : null;
    }

    /** Envia un boton del pad: 1 pulsado, 0 suelto. Solo habla cuando cambia de estado. */
    function send(id, on) {
      var was = active[id] === true;
      if (was === on) return;
      if (on) active[id] = true;
      else delete active[id];
      var gm = manager();
      if (!gm) return;
      try {
        // 32767 es para sticks analogicos (N64); un pad digital usa 1.
        gm.simulateInput(0, id, on ? 1 : 0);
      } catch (error) {
        // Motor a medio arrancar: el estado se reenvia en el siguiente movimiento.
      }
    }

    function releaseAll() {
      send(UP, false);
      send(DOWN, false);
      send(LEFT, false);
      send(RIGHT, false);
      send(BUTTON_A, false);
      send(BUTTON_B, false);
      sprintJumping = false;
      sprintRunning = false;
    }

    /**
     * Direcciones activas para un vector ya normalizado (-1..1, Y crece hacia abajo).
     * Funcion pura: es la que fija la orientacion del mando, y se puede probar sin emulador.
     */
    function directionsFor(dx, dy, dead) {
      var limit = typeof dead === 'number' ? dead : DEAD;
      var ids = [];
      if (dy <= -limit) ids.push(UP);
      if (dy >= limit) ids.push(DOWN);
      if (dx <= -limit) ids.push(LEFT);
      if (dx >= limit) ids.push(RIGHT);
      return ids;
    }

    function apply(ids) {
      send(UP, ids.indexOf(UP) !== -1);
      send(DOWN, ids.indexOf(DOWN) !== -1);
      send(LEFT, ids.indexOf(LEFT) !== -1);
      send(RIGHT, ids.indexOf(RIGHT) !== -1);
    }

    // --- Joystick ---------------------------------------------------------

    function stickMove(event, area) {
      var box = area.getBoundingClientRect();
      // Contenedor oculto o sin medir (gamepad apagado): no se inventa una direccion.
      if (box.width < 8 || box.height < 8) return;
      var radius = Math.max(1, Math.min(box.width, box.height) / 2);
      var dx = (event.clientX - (box.left + box.width / 2)) / radius;
      var dy = (event.clientY - (box.top + box.height / 2)) / radius;
      var length = Math.sqrt(dx * dx + dy * dy);
      if (length > 1) {
        dx /= length;
        dy /= length;
      }
      if (knob) {
        var travel = radius * 0.56;
        knob.style.transform = 'translate(' + Math.round(dx * travel) + 'px, ' + Math.round(dy * travel) + 'px)';
      }
      apply(directionsFor(dx, dy));
    }

    function buildStick(host) {
      var area = document.createElement('div');
      area.className = 'nahua-stick';
      knob = document.createElement('span');
      knob.className = 'nahua-stick-knob';
      area.appendChild(knob);

      area.addEventListener('pointerdown', function (event) {
        if (pointer !== null) return;
        pointer = event.pointerId;
        root.classList.add('nahua-pad-on');
        runCatching(function () { area.setPointerCapture(event.pointerId); });
        stickMove(event, area);
        haptic('press');
        event.preventDefault();
      });

      area.addEventListener('pointermove', function (event) {
        if (pointer !== event.pointerId) return;
        stickMove(event, area);
        event.preventDefault();
      });

      function end(event) {
        if (pointer !== event.pointerId) return;
        pointer = null;
        if (knob) knob.style.transform = '';
        root.classList.remove('nahua-pad-on');
        releaseAll();
      }

      area.addEventListener('pointerup', end);
      area.addEventListener('pointercancel', end);
      area.addEventListener('lostpointercapture', end);

      host.appendChild(area);
    }

    // --- Cruceta ----------------------------------------------------------

    function buildDpad(host) {
      var grid = document.createElement('div');
      grid.className = 'nahua-dpad';

      [
        { dir: 'up', id: UP, label: '▲' },
        { dir: 'left', id: LEFT, label: '◀' },
        { dir: 'right', id: RIGHT, label: '▶' },
        { dir: 'down', id: DOWN, label: '▼' }
      ].forEach(function (key) {
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'nahua-key';
        button.setAttribute('data-dir', key.dir);
        button.setAttribute('data-input', String(key.id));
        button.setAttribute('aria-label', key.dir);
        button.textContent = key.label;

        function press(event) {
          button.classList.add('nahua-key-on');
          send(key.id, true);
          haptic('press');
          event.preventDefault();
        }

        function release() {
          button.classList.remove('nahua-key-on');
          send(key.id, false);
        }

        button.addEventListener('pointerdown', press);
        button.addEventListener('pointerup', release);
        button.addEventListener('pointercancel', release);
        button.addEventListener('pointerleave', release);
        grid.appendChild(button);
      });

      var hub = document.createElement('span');
      hub.className = 'nahua-hub';
      grid.appendChild(hub);

      host.appendChild(grid);
    }

    // --- Correr + saltar en un solo gesto ---------------------------------

    /**
     * Se apoya el dedo: B (correr) queda pulsado. Se arrastra fuera del centro: A (saltar). Se
     * regresa al centro: se suelta A y se SIGUE corriendo, sin levantar el dedo. Es el gesto que
     * en un Game Boy se hace con el pulgar sobre B y A a la vez, pero aqui con una sola mano.
     */
    function buildSprint(host) {
      sprintEl = document.createElement('div');
      sprintEl.className = 'nahua-sprint';
      sprintKnob = document.createElement('span');
      sprintKnob.className = 'nahua-sprint-knob';
      var label = document.createElement('span');
      label.className = 'nahua-sprint-label';
      label.textContent = 'CORRER · SALTO';
      sprintEl.appendChild(sprintKnob);
      sprintEl.appendChild(label);

      function setJump(on) {
        if (sprintJumping === on) return;
        sprintJumping = on;
        send(BUTTON_A, on);
        sprintEl.classList.toggle('nahua-sprint-jump', on);
      }

      function move(event) {
        var box = sprintEl.getBoundingClientRect();
        if (box.width < 8) return;
        var cx = box.left + box.width / 2;
        var cy = box.top + box.height / 2;
        var dx = event.clientX - cx;
        var dy = event.clientY - cy;
        var distance = Math.sqrt(dx * dx + dy * dy);
        var radius = Math.max(1, Math.min(box.width, box.height) / 2);
        var travel = Math.min(distance, radius * 0.62);
        var angle = Math.atan2(dy, dx);
        if (sprintKnob) {
          sprintKnob.style.transform = 'translate(' + Math.round(Math.cos(angle) * travel) + 'px, ' +
            Math.round(Math.sin(angle) * travel) + 'px)';
        }
        // Histeresis: el salto entra al 34 % del radio y sale al 20 %. Sin esa holgura el salto
        // parpadea con el temblor del pulgar.
        if (!sprintJumping && distance > radius * 0.34) setJump(true);
        else if (sprintJumping && distance < radius * 0.2) setJump(false);
      }

      function end(event) {
        if (sprintPointer !== event.pointerId) return;
        sprintPointer = null;
        sprintRunning = false;
        setJump(false);
        send(BUTTON_B, false);
        sprintEl.classList.remove('nahua-sprint-on');
        if (sprintKnob) sprintKnob.style.transform = '';
      }

      sprintEl.addEventListener('pointerdown', function (event) {
        if (sprintPointer !== null) return;
        sprintPointer = event.pointerId;
        sprintRunning = true;
        send(BUTTON_B, true);
        sprintEl.classList.add('nahua-sprint-on');
        runCatching(function () { sprintEl.setPointerCapture(event.pointerId); });
        move(event);
        haptic('press');
        event.preventDefault();
      });
      sprintEl.addEventListener('pointermove', function (event) {
        if (sprintPointer !== event.pointerId) return;
        move(event);
        event.preventDefault();
      });
      sprintEl.addEventListener('pointerup', end);
      sprintEl.addEventListener('pointercancel', end);
      sprintEl.addEventListener('lostpointercapture', end);

      host.appendChild(sprintEl);
    }

    /**
     * Saca la capa del mando de #display (el rectangulo del juego) y la monta en #game, que ocupa
     * toda la pantalla. EmulatorJS la crea dentro de su propio contenedor y por eso los botones
     * no podian bajar mas alla de la imagen.
     */
    function liftLayer() {
      var emulator = window.EJS_emulator;
      var layer = emulator && emulator.virtualGamepad;
      var game = byId('game');
      if (!layer || !game) return null;
      if (layer.parentNode !== game) game.appendChild(layer);
      return layer;
    }

    // --- Montaje ----------------------------------------------------------

    function unmount() {
      releaseAll();
      pointer = null;
      knob = null;
      sprintPointer = null;
      var node = document.querySelector('.nahua-pad');
      if (node && node.parentNode) node.parentNode.removeChild(node);
      if (sprintEl && sprintEl.parentNode) sprintEl.parentNode.removeChild(sprintEl);
      sprintEl = null;
      sprintKnob = null;
      root = null;
    }

    function mount(host, config) {
      if (!host) return false;
      var layer = liftLayer();
      unmount();
      mode = config && config.pad === 'dpad' ? 'dpad' : 'joystick';
      root = document.createElement('div');
      root.className = 'nahua-pad';
      root.setAttribute('data-mode', mode);
      host.appendChild(root);
      if (mode === 'dpad') buildDpad(root);
      else buildStick(root);
      // El gesto de correr + saltar se apaga desde el panel si molesta.
      if (layer && (!config || config.sprint !== 'off')) buildSprint(layer);
      return true;
    }

    /**
     * EmulatorJS crea el gamepad en su constructor, sin evento: se busca el contenedor izquierdo
     * y, si todavia no existe, se reintenta. 30 s de margen cubren un arranque lento del nucleo.
     */
    function install(config) {
      var attempts = 0;
      if (timer) {
        window.clearInterval(timer);
        timer = 0;
      }

      function attempt() {
        // La capa del mando se mueve a #game en cuanto EmulatorJS la crea: si no, los botones
        // quedan encerrados en el rectangulo del juego.
        liftLayer();
        var host = document.querySelector('.ejs_virtualGamepad_left');
        if (host) {
          if (timer) {
            window.clearInterval(timer);
            timer = 0;
          }
          // Se remonta si cambio el tipo de mando, si falta el gesto de correr o si EmulatorJS
          // rehizo sus contenedores.
          var current = host.querySelector('.nahua-pad');
          var wantSprint = !config || config.sprint !== 'off';
          var hasSprint = !!document.querySelector('.nahua-sprint');
          if (current && current.getAttribute('data-mode') === (config.pad === 'dpad' ? 'dpad' : 'joystick') &&
              wantSprint === hasSprint) {
            return;
          }
          mount(host, config);
          return;
        }
        attempts += 1;
        if (attempts > 120 && timer) {
          window.clearInterval(timer);
          timer = 0;
        }
      }

      attempt();
      if (!document.querySelector('.nahua-pad')) timer = window.setInterval(attempt, 250);
    }

    /** Cambia joystick <-> cruceta sin reiniciar la partida. */
    function setMode(config) {
      var host = document.querySelector('.ejs_virtualGamepad_left');
      if (!host) {
        install(config);
        return false;
      }
      mount(host, config);
      return true;
    }

    return {
      install: install,
      setMode: setMode,
      releaseAll: releaseAll,
      directionsFor: directionsFor,
      active: function () { return Object.keys(active).map(Number); }
    };
  })();

  function runCatching(call) {
    try {
      call();
    } catch (error) {
      // setPointerCapture puede fallar si el puntero ya se solto.
    }
  }

  // A mano desde chrome://inspect: NahuaPad.active() dice que botones estan pulsados ahora.
  window.NahuaPad = NahuaPad;

  // --- Gamepad de EmulatorJS: solo botones --------------------------------
  //
  // La direccion la maneja NahuaPad, asi que aqui no se declara ningun control "left": el
  // contenedor queda vacio y es donde se monta el pad propio. A/B/Start/Select si son de
  // EmulatorJS, que los dibuja y los conecta como siempre.
  function gamepadSettings(core) {
    var buttons = [
      { type: 'button', text: 'B', id: 'b', location: 'right', left: 10, top: 70, bold: true, input_value: 0 },
      { type: 'button', text: 'A', id: 'a', location: 'right', left: 81, top: 40, bold: true, input_value: 8 },
      { type: 'button', text: 'Start', id: 'start', location: 'center', left: 60, fontSize: 15, block: true, input_value: 3 },
      { type: 'button', text: 'Select', id: 'select', location: 'center', left: -5, fontSize: 15, block: true, input_value: 2 }
    ];

    // L/R solo existen en Game Boy Advance: en GB/GBC se ven dos botones volando.
    if (core === 'gba') {
      buttons.push({ type: 'button', text: 'L', id: 'l', location: 'left', left: 3, top: -90, bold: true, block: true, input_value: 10 });
      buttons.push({ type: 'button', text: 'R', id: 'r', location: 'right', right: 3, top: -90, bold: true, block: true, input_value: 11 });
    }

    return buttons;
  }

  // --- Ajustes: juego -------------------------------------------------------
  //
  // Estas opciones las aplica el propio EmulatorJS (changeSettingOption) y se siembran en su
  // localStorage ANTES de arrancar, asi el motor las lee al construirse. Nada de reinventar el
  // menu del motor: se controla lo que de verdad se usa en el telefono.

  var GAME_DEFAULTS = { filter: 'pixel', ff: 'off', rewind: false, slot: 1, autosave: 300, fps: false };

  function readGameOptions() {
    var stored = readJson(OPTIONS_KEY, {});
    var options = {};
    var key;
    for (key in GAME_DEFAULTS) {
      if (Object.prototype.hasOwnProperty.call(GAME_DEFAULTS, key)) options[key] = GAME_DEFAULTS[key];
    }
    if (['pixel', 'lcd', 'crt', 'suave'].indexOf(stored.filter) !== -1) options.filter = stored.filter;
    if (['off', '2', '3', '5'].indexOf(stored.ff) !== -1) options.ff = stored.ff;
    options.rewind = stored.rewind === true;
    options.fps = stored.fps === true;
    options.slot = clampNumber(stored.slot, 1, 5, options.slot);
    options.autosave = clampNumber(stored.autosave, 0, 600, options.autosave);
    return options;
  }

  function saveGameOptions(options) { writeJson(OPTIONS_KEY, options); }

  function applyFilter(filter) {
    var display = byId('display');
    display.classList.remove('fx-pixel', 'fx-lcd', 'fx-crt', 'fx-suave');
    display.classList.add('fx-' + filter);
  }

  /** Opciones en caliente, cuando el motor ya esta arriba. */
  function applyLiveOptions(options) {
    applyFilter(options.filter);
    var emulator = window.EJS_emulator;
    if (!emulator || typeof emulator.changeSettingOption !== 'function') return;

    var ffEnabled = options.ff !== 'off';
    emulator.changeSettingOption('fastForward', ffEnabled ? 'enabled' : 'disabled');
    if (ffEnabled) emulator.changeSettingOption('ff-ratio', options.ff + '.0');
    emulator.changeSettingOption('fps', options.fps ? 'show' : 'hide');
    emulator.changeSettingOption('save-state-slot', String(options.slot));
    emulator.changeSettingOption('save-save-interval', String(options.autosave));
  }

  /**
   * Siembra la clave de ajustes de EmulatorJS (ejs-<id>-<core>-<juego>-settings) con nuestras
   * opciones. El motor lee rewindEnabled en su constructor, asi que esta es la unica forma de
   * que "rebobinar" aplique en el arranque; el resto se reaplica en caliente al empezar.
   */
  function seedEngineSettings(core, gameName, options) {
    try {
      var key = 'ejs-1-' + core + '-' + gameName + '-settings';
      var raw = window.localStorage.getItem(key);
      var data = raw ? JSON.parse(raw) : {};
      data = data && typeof data === 'object' ? data : {};
      data.settings = data.settings && typeof data.settings === 'object' ? data.settings : {};
      data.controlSettings = data.controlSettings || {};
      data.cheats = data.cheats || [];
      data.settings.rewindEnabled = options.rewind ? 'enabled' : 'disabled';
      data.settings['save-state-slot'] = String(options.slot);
      data.settings['save-save-interval'] = String(options.autosave);
      data.settings['ff-ratio'] = options.ff === 'off' ? '3.0' : options.ff + '.0';
      data.settings.fps = options.fps ? 'show' : 'hide';
      window.localStorage.setItem(key, JSON.stringify(data));
    } catch (error) {
      // Sin localStorage el motor arranca con sus valores por defecto.
    }
  }

  // --- Haptica --------------------------------------------------------------

  // EmulatorJS dibuja sus controles como divs (".ejs_virtualGamepad_button"), no como botones
  // nativos: el WebView no les da respuesta tactil. Nahua.haptic() la ejecuta en Kotlin con
  // performHapticFeedback sobre el propio WebView (sin permiso VIBRATE y respetando el ajuste
  // del sistema: si el usuario apago la respuesta tactil, no vibra, y eso es lo correcto).
  var HAPTIC_GAP_MS = 40;
  var lastHapticAt = 0;

  function haptic(style) {
    if (readPad().haptic === 'off') return;
    var now = Date.now();
    // Un dedo apoyado no debe zumbar sin fin: solo el primer contacto de cada pulsacion.
    if (now - lastHapticAt < HAPTIC_GAP_MS) return;
    lastHapticAt = now;
    if (hasSdk() && typeof window.Nahua.haptic === 'function') window.Nahua.haptic(style || 'press');
  }

  function wireHaptics() {
    // Delegacion en fase de captura: los controles los crea EmulatorJS despues de arrancar, asi
    // que no se pueden enganchar uno por uno. passive: no se toca el input del emulador.
    document.addEventListener('touchstart', function (event) {
      var target = event.target;
      if (!target || typeof target.closest !== 'function') return;
      if (!target.closest('.ejs_virtualGamepad_button')) return;
      haptic('press');
    }, { capture: true, passive: true });
  }

  // --- Telemetria -----------------------------------------------------------
  //
  // Mide frame pacing y lo que WebAudio reporta. No mide latencia de entrada: eso necesita el
  // dispositivo en la mano. Sale por NahuaNative.log -> logcat, etiqueta NahuaMiniApp, "[perf]".
  var FRAME_MS = 16.746;
  var audioContexts = [];

  /**
   * EmulatorJS crea su AudioContext y no lo expone. Se intercepta el constructor (antes de
   * inyectar loader.js) para poder leer sus latencias; el objeto devuelto es el original.
   */
  function installAudioProbe() {
    var Original = window.AudioContext || window.webkitAudioContext;
    if (!Original || Original.__nahuaProbe) return;
    function Probed() {
      var args = [null].concat(Array.prototype.slice.call(arguments));
      var ctx = new (Function.prototype.bind.apply(Original, args))();
      audioContexts.push(ctx);
      return ctx;
    }
    Probed.prototype = Original.prototype;
    Probed.__nahuaProbe = true;
    window.AudioContext = Probed;
    if (window.webkitAudioContext) window.webkitAudioContext = Probed;
  }

  var Perf = (function () {
    var running = false;
    var frameMs = [];
    var lastTs = 0;
    var startedAt = 0;
    var dropped = 0;
    var wasHidden = false;

    function log(line) {
      if (hasHost()) {
        try {
          window.NahuaNative.log(line);
          return;
        } catch (error) {
          // sin puente: cae a la consola del WebView
        }
      }
      if (window.console) console.log(line);
    }

    function tick(ts) {
      if (!running) return;
      var hiddenNow = document.hidden;
      // Con la pagina oculta el vsync se congela y al volver llega un delta gigante: ninguna de
      // las dos cosas describe el juego, asi que el reloj se reinicia.
      if (hiddenNow || wasHidden) {
        wasHidden = hiddenNow;
        lastTs = 0;
        window.requestAnimationFrame(tick);
        return;
      }
      var delta = lastTs ? ts - lastTs : 0;
      lastTs = ts;
      if (delta > 0) {
        frameMs.push(delta);
        if (delta > FRAME_MS * 1.5) dropped += Math.max(1, Math.round(delta / FRAME_MS) - 1);
      }
      window.requestAnimationFrame(tick);
    }

    function percentile(sorted, p) {
      if (!sorted.length) return 0;
      return Math.round(sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * p))] * 100) / 100;
    }

    function audioSummary() {
      var ctx = audioContexts[0];
      if (!ctx) return null;
      var toMs = function (value) {
        return typeof value === 'number' ? Math.round(value * 10000) / 10 : null;
      };
      return {
        sampleRate: ctx.sampleRate,
        state: ctx.state,
        baseLatencyMs: toMs(ctx.baseLatency),
        outputLatencyMs: toMs(ctx.outputLatency)
      };
    }

    return {
      start: function () {
        if (running) return;
        running = true;
        frameMs = [];
        dropped = 0;
        lastTs = 0;
        wasHidden = document.hidden;
        startedAt = window.performance.now();
        window.requestAnimationFrame(tick);
      },

      report: function (label) {
        if (!frameMs.length) return null;
        var elapsed = window.performance.now() - startedAt;
        var seconds = Math.round(elapsed / 100) / 10;
        var sorted = frameMs.slice().sort(function (a, b) { return a - b; });
        var sum = sorted.reduce(function (a, b) { return a + b; }, 0);
        var over = sorted.filter(function (delta) { return delta > FRAME_MS * 1.5; });
        var summary = {
          label: label || 'arcade',
          seconds: seconds,
          frames: sorted.length,
          fps: Math.round((sorted.length / (seconds || 1)) * 10) / 10,
          avgMs: Math.round((sum / sorted.length) * 100) / 100,
          p50Ms: percentile(sorted, 0.5),
          p95Ms: percentile(sorted, 0.95),
          maxMs: Math.round(sorted[sorted.length - 1] * 100) / 100,
          overBudgetPct: Math.round((over.length / sorted.length) * 1000) / 10,
          droppedFrames: dropped,
          audio: audioSummary()
        };
        log('[perf] ' + JSON.stringify(summary));
        return summary;
      },

      stop: function () {
        running = false;
        return this.report('stop');
      }
    };
  })();

  // A mano desde chrome://inspect: NahuaPerf.report('manual') / NahuaPerf.stop()
  window.NahuaPerf = Perf;

  // --- Pantalla del juego: proporcion original, franja libre y posicion propia ---
  //
  // La pantalla se queda con la proporcion del cartucho (GB/GBC 160x144, GBA 240x160): estirarla
  // a lo ancho deforma el juego. Y arranca DEBAJO de #gameBand, porque los telefonos actuales
  // tienen esquinas redondeadas y camara en el borde: ahi no se ve nada.
  //
  // La posicion vertical es SUYA (nahua.arcade.screen), no del mando: antes se centraba en el
  // hueco que dejaba el gamepad y por eso acomodar los controles movia la pantalla tambien.

  var SCREEN_RATIO = { gb: 160 / 144, gba: 240 / 160 };
  var SCREEN_KEY = 'nahua.arcade.screen';
  var SCREEN_DEFAULTS = { pos: 30 };
  var currentCore = 'gba';

  function readScreen() {
    var stored = readJson(SCREEN_KEY, {});
    return { pos: clampNumber(stored.pos, 0, 100, SCREEN_DEFAULTS.pos) };
  }

  function saveScreen(config) { writeJson(SCREEN_KEY, config); }

  function layoutScreen() {
    var host = byId('game');
    var band = byId('gameBand');
    var display = byId('display');
    if (!host || !display) return;

    var width = host.clientWidth || window.innerWidth || 360;
    var height = host.clientHeight || window.innerHeight || 640;
    var bandHeight = band ? band.offsetHeight : 0;
    if (width < 40 || height < 40) return;

    var position = readScreen().pos;
    var availableWidth = Math.max(80, width - 2 * 8);
    // Todo el alto entre la franja y el borde inferior: el mando flota encima y no participa.
    var availableHeight = Math.max(80, height - bandHeight - 12);

    var ratio = SCREEN_RATIO[currentCore] || SCREEN_RATIO.gba;
    var screenWidth = availableWidth;
    var screenHeight = Math.round(screenWidth / ratio);
    if (screenHeight > availableHeight) {
      screenHeight = availableHeight;
      screenWidth = Math.round(screenHeight * ratio);
    }

    // 0 % = pegada a la franja; 100 % = pegada al borde de abajo; 50 % = centro del hueco.
    var slack = Math.max(0, availableHeight - screenHeight);
    var offset = Math.round(slack * (clamp(position, 0, 100) / 100));

    display.style.width = screenWidth + 'px';
    display.style.height = screenHeight + 'px';
    display.style.marginTop = (6 + offset) + 'px';
  }

  function gameRatioFor(core) {
    currentCore = core === 'gb' ? 'gb' : 'gba';
    layoutScreen();
  }

  // --- Accesos de la franja superior ---------------------------------------

  var gameSettingsBtn = byId('gameSettings');
  var gamePadBtn = byId('gamePadBtn');

  if (gameSettingsBtn) {
    gameSettingsBtn.addEventListener('click', function () {
      // El menu del motor: guardados, trucos, avance rapido, captura, ajustes del nucleo.
      if (window.EJS_emulator && window.EJS_emulator.menu) window.EJS_emulator.menu.toggle();
      else toast('El menú del emulador aparece cuando arranca el juego');
    });
  }

  if (gamePadBtn) {
    gamePadBtn.addEventListener('click', function () { openPanel(true); });
  }

  // --- Arranque del juego ---------------------------------------------------

  /** Nombre de juego estable: el slug con su sha8, sin extension (sirve para las partidas). */
  function stableGameName(game) {
    return String(game.name || 'rom').replace(/\.(gba|agb|bin|gbc|gb)$/i, '');
  }

  /**
   * Fija la configuracion EJS_* y SOLO ENTONCES inyecta loader.js: el loader lee los globales
   * al ejecutarse, asi que inyectarlo antes de fijarlos lo deja con la configuracion vacia.
   */
  function startGame(game) {
    if (loaderInjected) return;

    var core = game.core === 'gb' ? 'gb' : 'gba';
    var gameName = stableGameName(game);
    var pad = readPad();
    var options = readGameOptions();

    window.EJS_player = '#display';
    window.EJS_core = core;                     // "gba" -> mgba, "gb" -> gambatte (GB + GBC).
    window.EJS_controlScheme = core === 'gb' ? 'gb' : 'gba';
    window.EJS_gameUrl = game.url;              // https del propio origen (o asset relativo).
    window.EJS_gameName = gameName;             // determinista: las partidas sobreviven.
    window.EJS_pathtodata = 'emulatorjs/';      // bundle vendored en el APK.
    window.EJS_startOnLoaded = true;
    window.EJS_language = 'es-ES';              // localizado y vendorizado.
    window.EJS_disableAutoLang = true;

    // Pantalla completa la maneja el anfitrion (el WebView no tiene Fullscreen API);
    // grabar/descargar/salir tocan APIs que no existen o rompen en WebView.
    window.EJS_Buttons = {
      playPause: true,
      restart: true,
      mute: true,
      settings: true,
      fullscreen: false,
      saveState: true,
      loadState: true,
      screenRecord: false,
      gamepad: true,
      cheat: false,
      volume: true,
      saveSavFiles: false,
      loadSavFiles: false,
      quickSave: true,
      quickLoad: true,
      screenshot: false,
      cacheManager: false,
      exitEmulation: false
    };

    // Gamepad de EmulatorJS: solo A/B(/L/R)/Start/Select. El mando direccional es propio
    // (NahuaPad) y se monta en el contenedor izquierdo, que queda vacio a proposito.
    window.EJS_VirtualGamepadSettings = gamepadSettings(core);

    // Las opciones del motor se siembran ANTES de construirlo (rebobinar se lee en el constructor).
    seedEngineSettings(core, gameName, options);
    applyPadLayout(pad);
    applyFilter(options.filter);
    lockPortrait();

    window.EJS_onGameStart = function () {
      applyLiveOptions(readGameOptions());
      NahuaPad.install(readPad());
      layoutScreen();
    };

    writeJson(LAST_KEY, {
      name: game.name,
      displayName: game.displayName,
      url: game.url,
      sizeBytes: game.sizeBytes,
      core: core,
      consoleLabel: game.consoleLabel,
      included: game.included === true
    });

    byId('home').classList.add('hidden');
    byId('game').classList.remove('hidden');
    // La pantalla se mide cuando el contenedor ya es visible: antes de montar el motor.
    gameRatioFor(core);
    Scene.pause();

    // Antes de inyectar el loader: el sondeo de AudioContext tiene que existir cuando EmulatorJS
    // construya el suyo, y la haptica debe estar enganchada cuando cree los controles.
    installAudioProbe();
    wireHaptics();

    // Ventana de medicion: 60 s de juego real y un resumen a logcat. NO se repite sola.
    Perf.start();
    window.setTimeout(function () { Perf.report('60s'); }, 60000);

    var script = document.createElement('script');
    script.src = 'emulatorjs/loader.js';
    script.onerror = function () {
      showOverlay(
        'Faltan archivos del emulador',
        'No se pudo cargar el motor desde el paquete local. Reinstala la app o repite la descarga.',
        'Volver al inicio'
      );
    };
    document.body.appendChild(script);
    loaderInjected = true;

    // El gamepad lo construye EmulatorJS al cargar el motor: se intenta ya y, si no esta, se
    // reintenta hasta que aparezca el contenedor izquierdo.
    NahuaPad.install(readPad());
  }

  // --- Errores --------------------------------------------------------------

  var overlay = byId('overlay');
  var overlayTitle = byId('overlayTitle');
  var overlayBody = byId('overlayBody');
  var overlayAction = byId('overlayAction');

  function showOverlay(title, body, actionLabel) {
    overlayTitle.textContent = title;
    overlayBody.textContent = body;
    overlayAction.textContent = actionLabel;
    overlay.classList.remove('hidden');
  }

  overlayAction.addEventListener('click', function () {
    // Un EmulatorJS a medio inicializar no se puede desmontar con confianza: recargar la
    // pagina es el reinicio limpio y conserva localStorage (seguir jugando).
    window.location.reload();
  });

  // --- Orientacion ----------------------------------------------------------

  /**
   * Vertical y ya. Un Game Boy es una consola vertical, y en horizontal el mando se comia media
   * pantalla y la geometria cambiaba a media partida. Se pide al anfitrion al entrar y al arrancar.
   */
  function lockPortrait() {
    if (hasSdk() && typeof window.Nahua.orientation === 'function') window.Nahua.orientation('portrait');
  }

  // --- Panel de ajustes -----------------------------------------------------

  var panel = byId('consolePanel');
  var pad = readPad();
  var options = readGameOptions();

  function markGroup(selector, isOn) {
    var nodes = panel.querySelectorAll(selector);
    var index;
    for (index = 0; index < nodes.length; index++) nodes[index].classList.toggle('seg-btn-on', isOn(nodes[index]));
  }

  function syncPanel() {
    pad = readPad();
    options = readGameOptions();

    markGroup('[data-filter]', function (node) { return node.getAttribute('data-filter') === options.filter; });
    markGroup('[data-pad]', function (node) { return node.getAttribute('data-pad') === pad.pad; });
    markGroup('[data-hand]', function (node) { return node.getAttribute('data-hand') === pad.hand; });
    markGroup('[data-haptic]', function (node) { return node.getAttribute('data-haptic') === pad.haptic; });
    markGroup('[data-sprint]', function (node) { return node.getAttribute('data-sprint') === pad.sprint; });
    markGroup('[data-ff]', function (node) { return node.getAttribute('data-ff') === options.ff; });
    markGroup('[data-rewind]', function (node) {
      return node.getAttribute('data-rewind') === (options.rewind ? 'on' : 'off');
    });
    markGroup('[data-slot]', function (node) { return Number(node.getAttribute('data-slot')) === options.slot; });
    markGroup('[data-autosave]', function (node) { return Number(node.getAttribute('data-autosave')) === options.autosave; });
    markGroup('[data-fps]', function (node) { return node.getAttribute('data-fps') === (options.fps ? 'on' : 'off'); });

    byId('ctlScale').value = String(Math.round(pad.scale * 100));
    byId('ctlScaleOut').textContent = Math.round(pad.scale * 100) + ' %';
    byId('ctlBottom').value = String(Math.round(pad.bottom));
    byId('ctlBottomOut').textContent = Math.round(pad.bottom) + ' px';
    byId('ctlSide').value = String(Math.round(pad.side));
    byId('ctlSideOut').textContent = Math.round(pad.side) + ' px';

    var screenPos = Math.round(readScreen().pos);
    byId('ctlScreenPos').value = String(screenPos);
    byId('ctlScreenPosOut').textContent = screenPos + ' %';
  }

  function updatePad(patch) {
    var next = readPad();
    var key;
    for (key in patch) {
      if (Object.prototype.hasOwnProperty.call(patch, key)) next[key] = patch[key];
    }
    savePad(next);
    // Tamano y posicion se ven al instante (CSS); el tipo de mando es estructura y espera al
    // siguiente arranque, porque EmulatorJS construye el gamepad una sola vez.
    applyPadLayout(next);
    syncPanel();
  }

  function updateOptions(patch, needsRestart) {
    var next = readGameOptions();
    var key;
    for (key in patch) {
      if (Object.prototype.hasOwnProperty.call(patch, key)) next[key] = patch[key];
    }
    saveGameOptions(next);
    applyLiveOptions(next);
    syncPanel();
    if (needsRestart) toast('Se aplica al reiniciar la partida');
  }

  function openPanel(open) {
    panel.classList.toggle('hidden', !open);
    if (open) syncPanel();
  }

  function switchTab(name) {
    var tabs = panel.querySelectorAll('[data-tab]');
    var bodies = panel.querySelectorAll('[data-panel]');
    var index;
    for (index = 0; index < tabs.length; index++) {
      tabs[index].classList.toggle('tab-on', tabs[index].getAttribute('data-tab') === name);
    }
    for (index = 0; index < bodies.length; index++) {
      bodies[index].classList.toggle('hidden', bodies[index].getAttribute('data-panel') !== name);
    }
  }

  if (panel) {
    syncPanel();
    Array.prototype.forEach.call(panel.querySelectorAll('[data-tab]'), function (tab) {
      tab.addEventListener('click', function () { switchTab(tab.getAttribute('data-tab')); });
    });
    Array.prototype.forEach.call(panel.querySelectorAll('[data-filter]'), function (node) {
      node.addEventListener('click', function () { updateOptions({ filter: node.getAttribute('data-filter') }, false); });
    });
    Array.prototype.forEach.call(panel.querySelectorAll('[data-pad]'), function (node) {
      node.addEventListener('click', function () {
        // Cambia en vivo: el pad es propio y se vuelve a montar sin reiniciar la partida.
        updatePad({ pad: node.getAttribute('data-pad') });
        NahuaPad.setMode(readPad());
      });
    });
    Array.prototype.forEach.call(panel.querySelectorAll('[data-hand]'), function (node) {
      node.addEventListener('click', function () {
        updatePad({ hand: node.getAttribute('data-hand') });
        // El cambio de mano es solo CSS (que contenedor va a cada lado): se aplica al instante.
        applyPadLayout(readPad());
      });
    });
    Array.prototype.forEach.call(panel.querySelectorAll('[data-haptic]'), function (node) {
      node.addEventListener('click', function () {
        updatePad({ haptic: node.getAttribute('data-haptic') });
        haptic('press');
      });
    });
    Array.prototype.forEach.call(panel.querySelectorAll('[data-sprint]'), function (node) {
      node.addEventListener('click', function () {
        updatePad({ sprint: node.getAttribute('data-sprint') });
        NahuaPad.setMode(readPad());
      });
    });
    Array.prototype.forEach.call(panel.querySelectorAll('[data-ff]'), function (node) {
      node.addEventListener('click', function () { updateOptions({ ff: node.getAttribute('data-ff') }, false); });
    });
    Array.prototype.forEach.call(panel.querySelectorAll('[data-rewind]'), function (node) {
      node.addEventListener('click', function () { updateOptions({ rewind: node.getAttribute('data-rewind') === 'on' }, true); });
    });
    Array.prototype.forEach.call(panel.querySelectorAll('[data-slot]'), function (node) {
      node.addEventListener('click', function () { updateOptions({ slot: Number(node.getAttribute('data-slot')) }, false); });
    });
    Array.prototype.forEach.call(panel.querySelectorAll('[data-autosave]'), function (node) {
      node.addEventListener('click', function () { updateOptions({ autosave: Number(node.getAttribute('data-autosave')) }, false); });
    });
    Array.prototype.forEach.call(panel.querySelectorAll('[data-fps]'), function (node) {
      node.addEventListener('click', function () { updateOptions({ fps: node.getAttribute('data-fps') === 'on' }, false); });
    });

    byId('ctlScale').addEventListener('input', function (event) {
      updatePad({ scale: clampNumber(Number(event.target.value) / 100, 0.7, 1.5, 1) });
    });
    byId('ctlBottom').addEventListener('input', function (event) {
      updatePad({ bottom: clampNumber(event.target.value, 0, 240, 50) });
    });
    byId('ctlSide').addEventListener('input', function (event) {
      updatePad({ side: clampNumber(event.target.value, 0, 140, 10) });
    });

    // Posicion de la pantalla: independiente del mando. Solo toca el canvas del juego.
    byId('ctlScreenPos').addEventListener('input', function (event) {
      saveScreen({ pos: clampNumber(event.target.value, 0, 100, SCREEN_DEFAULTS.pos) });
      layoutScreen();
      syncPanel();
    });

    byId('ctlReset').addEventListener('click', function () {
      savePad({});
      saveGameOptions({});
      saveScreen({});
      applyPadLayout(readPad());
      applyLiveOptions(readGameOptions());
      layoutScreen();
      syncPanel();
      toast('Ajustes restablecidos');
    });

    byId('ctlClose').addEventListener('click', function () { openPanel(false); });
    byId('ctlEjs').addEventListener('click', function () {
      openPanel(false);
      if (window.EJS_emulator && window.EJS_emulator.menu) window.EJS_emulator.menu.toggle();
      else toast('Los ajustes avanzados aparecen al iniciar un juego');
    });
    byId('ctlRestart').addEventListener('click', function () {
      // Reconstruye el gamepad con el tipo de mando elegido (la partida sigue en "Continuar").
      window.location.reload();
    });
    panel.addEventListener('click', function (event) {
      if (event.target === panel) openPanel(false);
    });
  }

  var consoleBtn = byId('consoleBtn');
  if (consoleBtn) consoleBtn.addEventListener('click', function () { openPanel(true); });

  // El boton Menu de la tira nativa abre este panel, y desde aqui se llega a los ajustes propios
  // de EmulatorJS.
  window.NahuaConsole = {
    open: function () { openPanel(true); },
    close: function () { openPanel(false); },
    toggle: function () {
      if (!panel) return false;
      openPanel(panel.classList.contains('hidden'));
      return true;
    },
    config: function () { return { pad: readPad(), game: readGameOptions() }; }
  };

  // --- Cambio de tamano: el mando y el escenario se recalculan -----------------

  (function watchViewport() {
    var pending = 0;

    function relayout() {
      pending = 0;
      applyPadLayout(readPad());
      layoutScreen();
      Scene.resize();
      // EmulatorJS escucha window.resize para su canvas y su menu; ademas se le avisa por si
      // el evento llego antes que el layout (WebView en modo inmersivo).
      var emulator = window.EJS_emulator;
      if (emulator && typeof emulator.handleResize === 'function') {
        try { emulator.handleResize(); } catch (error) { /* motor a medio arrancar */ }
      }
    }

    function schedule() {
      if (pending) window.clearTimeout(pending);
      // 140 ms: en Android el resize llega antes de que el WebView tenga el tamano final.
      pending = window.setTimeout(relayout, 140);
    }

    window.addEventListener('resize', schedule);
    window.addEventListener('orientationchange', schedule);
    if (window.visualViewport) window.visualViewport.addEventListener('resize', schedule);
  })();

  // --- Cableado de la portada ----------------------------------------------

  var pickBtn = byId('pickBtn');

  pickBtn.addEventListener('click', function () {
    if (!hasSdk() || typeof window.Nahua.pickRom !== 'function') {
      byId('hint').classList.remove('hidden');
      return;
    }
    pickBtn.disabled = true;
    pickBtn.textContent = 'Copiando…';
    window.Nahua.pickRom()
      .then(function (rom) {
        var console_ = consoleFor(rom.name);
        startGame({
          name: rom.name,
          displayName: rom.displayName,
          url: rom.url,
          sizeBytes: rom.sizeBytes,
          core: console_.core,
          consoleLabel: console_.label
        });
      })
      .catch(function (error) {
        pickBtn.disabled = false;
        pickBtn.textContent = 'Cargar ROM';
        var code = error && error.error;
        if (code === 'cancelled') return;           // cerro el selector: nada que decir.
        if (code === 'unsupported_rom') {
          toast('Ese archivo no es una ROM de GBA, GBC ni GB');
        } else if (code === 'too_large') {
          toast('La ROM supera los 64 MB');
        } else {
          toast('No se pudo importar la ROM');
        }
      });
  });

  continueBtn.addEventListener('click', function () {
    var last = readLast();
    if (last) startGame(last);
  });

  byId('exitBtn').addEventListener('click', function () {
    if (hasSdk()) window.Nahua.close();
    else window.history.back();
  });

  // --- Arranque -------------------------------------------------------------

  if (!hasHost()) {
    // Desarrollo en navegador: la pagina se navega, pero sin ROMs ni emulador real.
    byId('hint').classList.remove('hidden');
    pickBtn.disabled = true;
  }

  // La orientacion se fija ya en la portada, no solo al arrancar la partida.
  lockPortrait();
  applyPadLayout(pad);
  applyFilter(options.filter);
  renderBuiltin();
  renderContinue();
  refreshRoms();
  Scene.resize();
  Scene.start();
})();
