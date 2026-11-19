/*
 * Emulador GBA — lógica de la mini-app.
 * -------------------------------------------------------------
 * Dos pantallas: la portada (biblioteca de ROMs) y el juego (EmulatorJS).
 *
 * Reglas del anfitrión que esta página respeta:
 *  - Las ROMs nunca entran por la página: el usuario las elige con el selector del sistema
 *    (Nahua.pickRom), el host las copia a su almacenamiento privado y nos devuelve una URL
 *    https del propio origen. Sin file://, sin URIs de contenido.
 *  - El bundle del emulador vive en ./emulatorjs/ dentro del APK: todo offline. El anfitrión
 *    bloquea cualquier request a otro origen, asi que si falta un archivo se ve un error
 *    inmediato, no un timeout ni una fuga a cdn.emulatorjs.org.
 *  - EmulatorJS guarda partidas en IndexedDB del origen; el nombre del juego es el nombre
 *    estable de la ROM (con su sha8), asi que reimportar la misma ROM conserva las partidas.
 */
(function () {
  'use strict';

  var LAST_ROM_KEY = 'nahua.gba.lastRom';
  var loaderInjected = false;

  var home = document.getElementById('home');
  var game = document.getElementById('game');
  var continueCard = document.getElementById('continueCard');
  var continueName = document.getElementById('continueName');
  var continueBtn = document.getElementById('continueBtn');
  var romList = document.getElementById('romList');
  var romEmpty = document.getElementById('romEmpty');
  var pickBtn = document.getElementById('pickBtn');
  var exitBtn = document.getElementById('exitBtn');
  var hint = document.getElementById('hint');
  var overlay = document.getElementById('overlay');
  var overlayTitle = document.getElementById('overlayTitle');
  var overlayBody = document.getElementById('overlayBody');
  var overlayAction = document.getElementById('overlayAction');

  function hasHost() {
    return typeof window.NahuaNative === 'object' && window.NahuaNative !== null;
  }

  function hasSdk() {
    return typeof window.Nahua === 'object' && window.Nahua !== null;
  }

  // --- Portada: seguir jugando ----------------------------------------------

  function readLastRom() {
    try {
      var raw = window.localStorage.getItem(LAST_ROM_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (error) {
      return null;
    }
  }

  function renderContinue() {
    var last = readLastRom();
    if (!last || !last.url) {
      continueCard.classList.add('hidden');
      return;
    }
    continueName.textContent = last.displayName || last.name || 'Tu ROM';
    continueCard.classList.remove('hidden');
  }

  // --- Biblioteca -----------------------------------------------------------

  function romRow(rom) {
    var row = document.createElement('div');
    row.className = 'rom';

    var info = document.createElement('div');
    info.className = 'rom-info';

    var name = document.createElement('p');
    name.className = 'rom-name';
    name.textContent = rom.displayName || rom.name;

    var size = document.createElement('p');
    size.className = 'rom-size';
    size.textContent = formatBytes(rom.sizeBytes);

    info.appendChild(name);
    info.appendChild(size);

    var play = document.createElement('button');
    play.type = 'button';
    play.className = 'btn primary small';
    play.textContent = 'Jugar';
    play.addEventListener('click', function () { startGame(rom); });

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
      window.Nahua.deleteRom(rom.name).then(refreshRoms).catch(function (error) {
        if (error && error.error === 'not_found') refreshRoms();
      });
    });

    row.appendChild(info);
    row.appendChild(play);
    row.appendChild(del);
    return row;
  }

  function formatBytes(bytes) {
    var value = Number(bytes);
    if (!isFinite(value) || value <= 0) return '';
    if (value < 1024 * 1024) return (value / 1024).toFixed(0) + ' KB';
    return (value / (1024 * 1024)).toFixed(1) + ' MB';
  }

  function renderRoms(roms) {
    var rows = romList.querySelectorAll('.rom');
    var index;
    for (index = 0; index < rows.length; index++) rows[index].remove();

    var list = Array.isArray(roms) ? roms : [];
    romEmpty.classList.toggle('hidden', list.length > 0);
    list.forEach(function (rom) {
      romList.appendChild(romRow(rom));
    });

    // Si la ROM "continuar" fue borrada, retirar la tarjeta.
    var last = readLastRom();
    if (last && list.length > 0 && !list.some(function (rom) { return rom.name === last.name; })) {
      window.localStorage.removeItem(LAST_ROM_KEY);
      renderContinue();
    }
    if (list.length === 0) {
      window.localStorage.removeItem(LAST_ROM_KEY);
      renderContinue();
    }
  }

  function refreshRoms() {
    if (!hasSdk()) return;
    window.Nahua.listRoms()
      .then(function (result) { renderRoms(result.roms); })
      .catch(function () {
        // El anfitrión v1 no trae las funciones de ROMs: avisar sin romper la portada.
        hint.classList.remove('hidden');
      });
  }

  // --- Háptica --------------------------------------------------------------

  // EmulatorJS dibuja sus controles como divs (".ejs_virtualGamepad_button"), no como botones
  // nativos: el WebView no les da respuesta táctil. Nahua.haptic() la ejecuta en Kotlin con
  // performHapticFeedback sobre el propio WebView (sin permiso VIBRATE y respetando el ajuste
  // del sistema: si el usuario apagó la respuesta táctil, no vibra, y eso es lo correcto).
  var HAPTIC_GAP_MS = 40;
  var lastHapticAt = 0;

  function haptic(style) {
    var now = Date.now();
    // Un dedo apoyado no debe zumbar sin fin: solo el primer contacto de cada pulsación.
    if (now - lastHapticAt < HAPTIC_GAP_MS) return;
    lastHapticAt = now;
    if (hasSdk() && typeof window.Nahua.haptic === 'function') window.Nahua.haptic(style || 'press');
  }

  function wireHaptics() {
    // Delegación en fase de captura: los controles los crea EmulatorJS después de arrancar, así
    // que no se pueden enganchar uno por uno. passive: no se toca el input del emulador.
    document.addEventListener('touchstart', function (event) {
      var target = event.target;
      if (!target || typeof target.closest !== 'function') return;
      if (!target.closest('.ejs_virtualGamepad_button')) return;
      haptic('press');
    }, { capture: true, passive: true });
  }

  // --- Telemetría (decidir la Fase 2 con números, no con corazonadas) --------
  //
  // Qué mide y qué NO mide, para no leer de más el reporte:
  //  - Mide frame pacing: cuántos frames se pasan del presupuesto y por cuánto. Un frame de GBA
  //    dura 16.746 ms (16.777216 MHz / 280896 ciclos por frame), pero el vsync de la pantalla
  //    marca 16.67 ms: requestAnimationFrame no puede distinguir 59.7275 de 60 Hz, así que la
  //    deriva exacta del reloj del GBA contra el de la pantalla NO sale de aquí.
  //  - Mide lo que WebAudio reporta (baseLatency/outputLatency/sampleRate): es el piso de
  //    latencia que añade Chromium antes de tocar AAudio.
  //  - No mide latencia de entrada: eso necesita el dispositivo en la mano.
  // Sale por NahuaNative.log -> logcat, etiqueta NahuaMiniApp, prefijo "[perf]".
  var GBA_FRAME_MS = 16.746;
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
    Probed.prototype = Original.prototype;   // instanceof y chequeos de tipo siguen funcionando
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
      // Con la página oculta (app en segundo plano) el vsync se congela y al volver llega un
      // delta gigante: ninguna de las dos cosas describe el juego, así que el reloj se reinicia.
      if (hiddenNow || wasHidden) {
        wasHidden = hiddenNow;
        lastTs = 0;
        window.requestAnimationFrame(tick);
        return;
      }
      var delta = lastTs ? ts - lastTs : 0;
      lastTs = ts;
      // delta <= 0 = timestamp repetido (algunos WebView lo hacen al reanudar): no es un frame.
      if (delta > 0) {
        frameMs.push(delta);
        // Se cuenta como frame perdido lo que excede 1.5 frames: por debajo es ruido del vsync.
        if (delta > GBA_FRAME_MS * 1.5) {
          dropped += Math.max(1, Math.round(delta / GBA_FRAME_MS) - 1);
        }
      }
      window.requestAnimationFrame(tick);
    }

    function percentile(sorted, p) {
      if (!sorted.length) return 0;
      var index = Math.min(sorted.length - 1, Math.floor(sorted.length * p));
      return Math.round(sorted[index] * 100) / 100;
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
        var over = sorted.filter(function (delta) { return delta > GBA_FRAME_MS * 1.5; });
        var summary = {
          label: label || 'gba',
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

  // --- Arranque del juego ---------------------------------------------------

  /**
   * Fija la configuración EJS_* y SOLO ENTONCES inyecta loader.js: el loader lee los globales
   * al ejecutarse, asi que inyectarlo antes de fijarlos lo deja con la configuración vacía.
   */
  function startGame(rom) {
    if (loaderInjected) return;

    window.EJS_player = '#display';
    window.EJS_core = 'gba';                    // EmulatorJS mapea "gba" -> nucleo mgba.
    window.EJS_gameUrl = rom.url;               // https del propio origen (asset loader).
    window.EJS_gameName = stableGameName(rom);  // determinista: las partidas sobreviven.
    window.EJS_pathtodata = 'emulatorjs/';      // bundle vendored en el APK.
    window.EJS_startOnLoaded = true;
    window.EJS_language = 'es-ES';              // localizado y vendorizado.
    window.EJS_disableAutoLang = true;

    // Pantalla completa la maneja el anfitrión (el WebView no tiene Fullscreen API);
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

    // Sin EJS_VirtualGamepadSettings a propósito: EmulatorJS trae la disposición táctil
    // correcta de GBA (A/B, cruceta, L/R, Start/Select) para el esquema "gba".

    window.localStorage.setItem(LAST_ROM_KEY, JSON.stringify(rom));

    home.classList.add('hidden');
    game.classList.remove('hidden');

    // Antes de inyectar el loader: el sondeo de AudioContext tiene que existir cuando EmulatorJS
    // construya el suyo, y la háptica debe estar enganchada cuando cree los controles.
    installAudioProbe();
    wireHaptics();

    // Ventana de medición: 60 s de juego real y un resumen a logcat. NO se repite sola; para
    // volver a medir, NahuaPerf.start() desde chrome://inspect.
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
  }

  /** Nombre de juego estable: el slug con su sha8, sin extension. */
  function stableGameName(rom) {
    var raw = String(rom.name || 'rom');
    return raw.replace(/\.gba$/i, '');
  }

  // --- Errores --------------------------------------------------------------

  function showOverlay(title, body, actionLabel) {
    overlayTitle.textContent = title;
    overlayBody.textContent = body;
    overlayAction.textContent = actionLabel;
    overlay.classList.remove('hidden');
  }

  overlayAction.addEventListener('click', function () {
    // Un EmulatorJS a medio inicializar no se puede desmontar con confianza: recargar la
    // página es el reinicio limpio y conserva localStorage (seguir jugando).
    window.location.reload();
  });

  // --- Cableado -------------------------------------------------------------

  pickBtn.addEventListener('click', function () {
    if (!hasSdk()) {
      hint.classList.remove('hidden');
      return;
    }
    pickBtn.disabled = true;
    pickBtn.textContent = 'Copiando…';
    window.Nahua.pickRom()
      .then(function (rom) {
        startGame(rom);
      })
      .catch(function (error) {
        pickBtn.disabled = false;
        pickBtn.textContent = 'Cargar ROM (.gba)';
        var code = error && error.error;
        if (code === 'cancelled') return;           // cerró el selector: nada que decir.
        if (code === 'unsupported_rom') {
          window.Nahua.toast('Ese archivo no es una ROM de GBA');
        } else if (code === 'too_large') {
          window.Nahua.toast('La ROM supera los 64 MB');
        } else {
          window.Nahua.toast('No se pudo importar la ROM');
        }
      });
  });

  continueBtn.addEventListener('click', function () {
    var last = readLastRom();
    if (last && last.url) startGame(last);
  });

  exitBtn.addEventListener('click', function () {
    if (hasSdk()) {
      window.Nahua.close();
    } else {
      window.history.back();
    }
  });

  // --- Arranque -------------------------------------------------------------

  if (!hasHost()) {
    // Desarrollo en navegador: la página se navega, pero sin ROMs ni emulador real.
    hint.classList.remove('hidden');
    pickBtn.disabled = true;
  }

  renderContinue();
  refreshRoms();
})();
