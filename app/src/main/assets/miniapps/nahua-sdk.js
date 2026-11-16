/*
 * Nahua Mini App SDK (v1)
 * -------------------------------------------------------------
 * The host injects window.NahuaNative (addJavascriptInterface). This script turns the raw
 * bridge into a small promise-based API. Keep the surface tiny: session, payments, toast, close.
 */
(function () {
  'use strict';

  var pending = Object.create(null);
  var sequence = 0;

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

  function resolve(requestId, rawPayload) {
    var entry = pending[requestId];
    if (!entry) return;
    delete pending[requestId];
    var data;
    try {
      data = typeof rawPayload === 'string' ? JSON.parse(rawPayload) : rawPayload;
    } catch (error) {
      entry.reject({ status: 'error', error: 'bad_payload' });
      return;
    }
    if (data && data.status === 'ok') {
      entry.resolve(data);
    } else {
      entry.reject(data || { status: 'error', error: 'unknown' });
    }
  }

  window.Nahua = {
    version: '1.0.0',
    _resolve: resolve,

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
      window.NahuaNative.toast(String(message));
    },

    /**
     * Requests the device location (permission handled by the host).
     * @returns {Promise<{status: string, lat: number, lng: number, accuracy: number}>}
     */
    location: function () {
      return post('requestLocation', {});
    },

    /** Closes the mini app and returns to the Nahua home. */
    close: function () {
      window.NahuaNative.close();
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