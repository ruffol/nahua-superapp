/*
 * Nahua Mini App SDK (v1.4)
 * -------------------------------------------------------------
 * The host injects window.NahuaNative (addJavascriptInterface). This script turns the raw
 * bridge into a small promise-based API: session, payments, one-shot and live location,
 * external directions, clipboard, háptica, barter valuation, inbox notices, toast, close y,
 * desde v1.3, la gestion de ROMs del emulador (pickRom, listRoms, deleteRom).
 *
 * Location in the browser (dev) falls back to navigator.geolocation, so the map can be
 * tested outside the APK. Valuation returns null without a host: callers keep their own
 * estimate and the page still renders.
 */
(function () {
  'use strict';

  var pending = Object.create(null);
  var sequence = 0;
  var listeners = { location: [], locationError: [] };
  var browserWatchId = null;

  function post(method, payload) {
    return new Promise(function (resolve, reject) {
      var id = 'req_' + (++sequence);
      pending[id] = { resolve: resolve, reject: reject };
      try {
        window.NahuaNative[method](JSON.stringify(payload), id);
      } catch (error) {
        delete pending[id];
        reject(error);
      }
    });
  }

  function parsePayload(raw) {
    if (raw && typeof raw === 'object') return raw;
    try {
      return typeof raw === 'string' ? JSON.parse(raw) : {};
    } catch (error) {
      return {};
    }
  }

  function resolve(requestId, rawPayload) {
    var entry = pending[requestId];
    if (!entry) return;
    delete pending[requestId];
    var data = parsePayload(rawPayload);
    if (data && data.status === 'ok') {
      entry.resolve(data);
    } else {
      entry.reject(data || { status: 'error', error: 'unknown' });
    }
  }

  function emit(event, rawPayload) {
    var data = parsePayload(rawPayload);
    var list = listeners[event] || [];
    list.slice().forEach(function (handler) {
      try {
        handler(data);
      } catch (error) {
        // Un listener roto no debe tumbar el resto de la página.
        if (window.console) console.error('Nahua listener', event, error);
      }
    });
  }

  function startBrowserWatch() {
    if (browserWatchId !== null) return;
    if (!navigator.geolocation) {
      emit('locationError', { status: 'error', error: 'unavailable' });
      return;
    }
    browserWatchId = navigator.geolocation.watchPosition(
      function (position) {
        emit('location', {
          status: 'ok',
          lat: position.coords.latitude,
          lng: position.coords.longitude,
          accuracy: position.coords.accuracy,
          speed: position.coords.speed,
          bearing: position.coords.heading,
          altitude: position.coords.altitude,
          timestamp: position.timestamp
        });
      },
      function (error) {
        emit('locationError', {
          status: 'error',
          error: error && error.code === 1 ? 'permission_denied' : 'unavailable'
        });
      },
      { enableHighAccuracy: true, maximumAge: 0, timeout: 20000 }
    );
  }

  function stopBrowserWatch() {
    if (browserWatchId !== null && navigator.geolocation) {
      navigator.geolocation.clearWatch(browserWatchId);
    }
    browserWatchId = null;
  }

  window.Nahua = {
    version: '1.3.0',
    _resolve: resolve,
    _emit: emit,

    /** Profile of the signed-in user, provided by the host session. */
    user: function () {
      try {
        return JSON.parse(window.NahuaNative.getSession());
      } catch (error) {
        return {};
      }
    },

    /** Native toast / snackbar. */
    toast: function (message) {
      try {
        window.NahuaNative.toast(String(message));
      } catch (error) {
        if (window.console) console.log('Nahua:', message);
      }
    },

    /**
     * Requests one device location (permission handled by the host).
     * @returns {Promise<{status: string, lat: number, lng: number, accuracy: number}>}
     */
    location: function () {
      return post('requestLocation', {});
    },

    /**
     * Starts live GPS updates. The returned function stops this watcher.
     *
     * @param {(fix: {lat: number, lng: number, accuracy: number, speed?: number,
     *                bearing?: number, altitude?: number, timestamp: number}) => void} handler
     * @param {{intervalMs?: number}} [options]
     * @returns {() => void}
     */
    watchLocation: function (handler, options) {
      if (typeof handler !== 'function') {
        throw new TypeError('watchLocation necesita una función');
      }
      var interval = Math.max(500, Math.min(15000, Number(options && options.intervalMs) || 1500));
      listeners.location.push(handler);
      if (listeners.location.length === 1) {
        if (window.NahuaNative && window.NahuaNative.startLocationUpdates) {
          window.NahuaNative.startLocationUpdates(interval);
        } else {
          startBrowserWatch(interval);
        }
      }
      var active = true;
      return function stop() {
        if (!active) return;
        active = false;
        var index = listeners.location.indexOf(handler);
        if (index >= 0) listeners.location.splice(index, 1);
        if (listeners.location.length === 0) {
          if (window.NahuaNative && window.NahuaNative.stopLocationUpdates) {
            window.NahuaNative.stopLocationUpdates();
          } else {
            stopBrowserWatch();
          }
        }
      };
    },

    /** Listens for watcher errors (permission denied, GPS off, no provider). */
    onLocationError: function (handler) {
      if (typeof handler !== 'function') return function () {};
      listeners.locationError.push(handler);
      return function () {
        var index = listeners.locationError.indexOf(handler);
        if (index >= 0) listeners.locationError.splice(index, 1);
      };
    },

    /** Stops every live watcher started from this page. */
    stopLocation: function () {
      listeners.location.length = 0;
      if (window.NahuaNative && window.NahuaNative.stopLocationUpdates) {
        window.NahuaNative.stopLocationUpdates();
      } else {
        stopBrowserWatch();
      }
    },

    /**
     * Opens the platform maps app with driving directions to the destination.
     * @param {number} lat
     * @param {number} lng
     * @param {string} [label]
     */
    openDirections: function (lat, lng, label) {
      var payload = { lat: Number(lat), lng: Number(lng), label: String(label || 'Destino') };
      if (!isFinite(payload.lat) || !isFinite(payload.lng)) return;
      if (window.NahuaNative && window.NahuaNative.openDirections) {
        window.NahuaNative.openDirections(JSON.stringify(payload));
        return;
      }
      var url = 'https://www.google.com/maps/dir/?api=1&destination=' + payload.lat + ',' + payload.lng;
      window.open(url, '_blank');
    },

    /**
     * Pide al anfitrión la orientación de la pantalla.
     * @param {string} [mode] auto | portrait | landscape
     * @returns {boolean} false si no hay anfitrión.
     */
    orientation: function (mode) {
      var value = String(mode || 'auto').toLowerCase();
      if (value !== 'auto' && value !== 'portrait' && value !== 'landscape') value = 'auto';
      try {
        if (window.NahuaNative && window.NahuaNative.orientation) {
          window.NahuaNative.orientation(value);
          return true;
        }
      } catch (error) {
        // La orientación nunca debe romper una pulsación.
      }
      return false;
    },

    /**
     * Retroalimentación háptica. Un control dibujado en HTML no vibra por sí solo: el host
     * ejecuta performHapticFeedback sobre el WebView (sin permiso VIBRATE y respetando el ajuste
     * del sistema, así que en silencio/molesto sigue funcionando bien).
     *
     * @param {string} [style] tap | press | long | confirm | reject
     * @returns {boolean} false si no hay anfitrión ni vibración del navegador.
     */
    haptic: function (style) {
      var value = String(style || 'press');
      try {
        if (window.NahuaNative && window.NahuaNative.haptic) {
          window.NahuaNative.haptic(value);
          return true;
        }
        // Navegador de escritorio (desarrollo): imitación inofensiva si el equipo vibra.
        if (navigator.vibrate) {
          navigator.vibrate(value === 'long' ? 24 : 8);
          return true;
        }
      } catch (error) {
        // La háptica nunca debe romper una pulsación.
      }
      return false;
    },

    /** Copies text to the system clipboard through the host (fallback: navigator.clipboard). */
    copy: function (text) {
      var value = String(text == null ? '' : text);
      try {
        if (window.NahuaNative && window.NahuaNative.copy) {
          window.NahuaNative.copy(value);
          return Promise.resolve(true);
        }
        if (navigator.clipboard && navigator.clipboard.writeText) {
          return navigator.clipboard.writeText(value).then(function () { return true; });
        }
      } catch (error) {
        // cae al catch del llamador
      }
      return Promise.reject(new Error('clipboard_unavailable'));
    },

    /**
     * Valora un objeto del bazar. La aritmética vive en el anfitrión (BarterValuator.kt) y esta
     * llamada es síncrona: el formulario de publicación recalcula al escribir.
     *
     * @param {{referenceCents: number, category?: string, condition?: string, ageMonths?: number,
     *          complete?: boolean, hasReceipt?: boolean}} item
     * @returns {{status: string, estimatedCents: number, lowCents: number, highCents: number,
     *            barterMinCents: number, confidence: string, factors: Array}|null}
     */
    valuate: function (item) {
      try {
        if (window.NahuaNative && window.NahuaNative.valuate) {
          return JSON.parse(window.NahuaNative.valuate(JSON.stringify(item || {})));
        }
      } catch (error) {
        if (window.console) console.log('Nahua.valuate no disponible', error);
      }
      return null;
    },

    /**
     * Compara dos objetos valorados y dice si el trueque conviene, está parejo o pide compensación.
     *
     * @param {object} mine    objeto que doy
     * @param {object} theirs  objeto que recibo
     * @returns {{verdict: string, deltaCents: number, cashTopUpCents: number, message: string}|null}
     */
    compare: function (mine, theirs) {
      try {
        if (window.NahuaNative && window.NahuaNative.compareBarter) {
          return JSON.parse(window.NahuaNative.compareBarter(JSON.stringify({
            mine: mine || {},
            theirs: theirs || {}
          })));
        }
      } catch (error) {
        if (window.console) console.log('Nahua.compare no disponible', error);
      }
      return null;
    },

    /**
     * Deja un aviso en la bandeja nativa (Avisos): "a <usuario> le interesa truequear tu <objeto>".
     * @param {{title: string, body: string}} options
     * @returns {Promise<{status: string}>}
     */
    announce: function (options) {
      return post('announceBarter', {
        title: String((options && options.title) || '').slice(0, 90),
        body: String((options && options.body) || '').slice(0, 240)
      });
    },

    /**
     * Abre el selector de ROMs del sistema. El host copia el archivo a su almacenamiento
     * privado y resuelve con la URL https desde la que el WebView lo sirve.
     * @returns {Promise<{status: string, name: string, displayName: string,
     *                    sizeBytes: number, url: string}>}
     * @rejects {{status: string, error: string}} cancelled | unsupported_rom | too_large |
     *           read_failed | write_failed | unavailable
     */
    pickRom: function () {
      return post('pickRom', {});
    },

    /**
     * ROMs ya importadas, la mas reciente primero.
     * @returns {Promise<{status: string, roms: Array<{name, displayName, sizeBytes, url}>}>}
     */
    listRoms: function () {
      return post('listRoms', {});
    },

    /**
     * Borra una ROM importada. El nombre se valida en el host: nada fuera de roms/ es tocable.
     * @param {string} name  el "name" devuelto por pickRom/listRoms.
     * @returns {Promise<{status: string}>}
     * @rejects {{status: string, error: string}} invalid_name | not_found
     */
    deleteRom: function (name) {
      return post('deleteRom', { name: String(name == null ? '' : name) });
    },

    /** Closes the mini app and returns to the Nahua home. */
    close: function () {
      try {
        window.NahuaNative.close();
      } catch (error) {
        window.history.back();
      }
    },

    /**
     * Charges the user wallet after an explicit confirmation in the host UI.
     * @param {{amount: number, concept: string, merchant?: string}} options amount in pesos.
     * @returns {Promise<{status: string, transactionId: string, balanceCents: number}>}
     */
    pay: function (options) {
      var amount = Number(options && options.amount);
      if (!isFinite(amount) || amount <= 0) {
        return Promise.reject({ status: 'error', error: 'invalid_amount' });
      }
      return post('pay', {
        amountCents: Math.round(amount * 100),
        concept: String((options && options.concept) || 'Pago en mini app'),
        merchant: String((options && options.merchant) || 'Nahua')
      });
    }
  };
})();
