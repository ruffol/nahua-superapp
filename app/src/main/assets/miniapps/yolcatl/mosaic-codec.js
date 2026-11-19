/*
 * YOLCATL · mosaic-codec.js
 * Mosaico cromático artístico 16x16 (256 teselas, 3 bits/celda, 8 colores).
 *
 * 32 celdas de calibración (4 esquinas 2x2 + 4 anclas de borde 2x2) dejan 224 celdas útiles
 * = 672 bits = 84 bytes. El alma se empaqueta ahí con CRC-16 y se puede exportar como imagen
 * o como código de texto (base64url), sin red y sin servidores.
 */
(function (global) {
  'use strict';

  var GRID = 16;
  var USABLE_BYTES = 84;
  var SCHEMA = 2;
  var CODE_PREFIX = 'YLC2';

  // Paleta de 8 colores de alto contraste (3 bits por celda).
  var PALETTE = [
    '#121020', // 0 tinta
    '#F6EEDC', // 1 crema
    '#FF5B4A', // 2 coral
    '#F4B32B', // 3 ámbar
    '#1E9E6A', // 4 jade
    '#2F6BFF', // 5 azul
    '#A259E6', // 6 violeta
    '#6C7F94'  // 7 pizarra
  ];

  // Anclas de calibración: fijan orientación y balance de color al leer la imagen.
  var ANCHORS = [
    { row: 0, col: 0, color: 0 },  { row: 0, col: 14, color: 1 },
    { row: 14, col: 14, color: 2 }, { row: 14, col: 0, color: 3 },
    { row: 0, col: 7, color: 4 },  { row: 7, col: 14, color: 5 },
    { row: 14, col: 7, color: 6 }, { row: 7, col: 0, color: 7 }
  ];

  function isReserved(row, col) {
    if ((row <= 1 || row >= 14) && (col <= 1 || col >= 14)) return true;
    if ((row <= 1 || row >= 14) && (col >= 7 && col <= 8)) return true;
    if ((col <= 1 || col >= 14) && (row >= 7 && row <= 8)) return true;
    return false;
  }

  function dataCells() {
    var cells = [];
    for (var row = 0; row < GRID; row++) {
      for (var col = 0; col < GRID; col++) {
        if (!isReserved(row, col)) cells.push({ row: row, col: col });
      }
    }
    return cells;
  }
  var CELLS = dataCells();

  function crc16(bytes, length) {
    var crc = 0xFFFF;
    for (var i = 0; i < length; i++) {
      crc ^= (bytes[i] & 0xFF) << 8;
      for (var bit = 0; bit < 8; bit++) {
        crc = (crc & 0x8000) ? ((crc << 1) ^ 0x1021) : (crc << 1);
        crc &= 0xFFFF;
      }
    }
    return crc;
  }

  function speciesIndex(id) {
    var list = (global.YolcatlSoul && global.YolcatlSoul.SPECIES) || [];
    for (var i = 0; i < list.length; i++) if (list[i].id === id) return i;
    return 0;
  }

  function sceneIndex(id) {
    var list = (global.YolcatlSoul && global.YolcatlSoul.SCENES) || [];
    for (var i = 0; i < list.length; i++) if (list[i].id === id) return i;
    return 0;
  }

  function accessoryByte(soul, index) {
    var list = (global.YolcatlSoul && global.YolcatlSoul.ACCESSORIES) || [];
    var item = list[index];
    if (!item) return 0;
    var byte = 0;
    if (soul.unlocked.indexOf(item.id) >= 0) byte |= 1;
    if (soul.equipped.indexOf(item.id) >= 0) byte |= 2;
    return byte;
  }

  /** Empaqueta el alma en exactamente 84 bytes. */
  function pack(soul) {
    var bytes = new Uint8Array(USABLE_BYTES);
    var win = global.YolcatlSoul ? global.YolcatlSoul.xpWindow(soul.xp) : { level: 1, into: 0 };
    bytes[0] = SCHEMA;

    var name = String(soul.name || 'Xolito').slice(0, 12);
    for (var i = 0; i < name.length; i++) bytes[1 + i] = name.charCodeAt(i) & 0xFF;

    bytes[13] = speciesIndex(soul.species);

    var genes = ['curiosity', 'social', 'vitality', 'appetite'];
    for (var g = 0; g < genes.length; g++) {
      bytes[14 + g] = Math.max(0, Math.min(255, Math.round(soul.genes[genes[g]] || 0)));
    }

    bytes[18] = win.level;
    bytes[19] = Math.max(0, Math.min(255, win.into));

    var stats = ['food', 'mood', 'energy', 'clean'];
    for (var s = 0; s < stats.length; s++) {
      bytes[20 + s] = Math.max(0, Math.min(100, Math.round(soul[stats[s]])));
    }

    bytes[24] = sceneIndex(soul.scene);
    for (var a = 0; a < 6; a++) bytes[25 + a] = accessoryByte(soul, a);

    var memory = soul.memory || {};
    bytes[31] = Math.max(0, Math.min(100, Math.round(memory.affection || 0)));
    bytes[32] = sceneIndex(memory.favoriteScene || soul.scene);
    var favIndex = 0;
    var accessories = (global.YolcatlSoul && global.YolcatlSoul.ACCESSORIES) || [];
    for (var f = 0; f < accessories.length; f++) {
      if (accessories[f].id === memory.favoriteAccessory) favIndex = f + 1;
    }
    bytes[33] = favIndex;
    bytes[34] = Math.min(255, Math.round((memory.touches || 0) / 5));
    bytes[35] = Math.min(255, Math.round((memory.plays || 0) / 5));
    bytes[36] = Math.min(255, Math.round((memory.feeds || 0) / 5));
    bytes[37] = Math.min(255, Math.round((memory.baths || 0) / 5));
    var days = Math.min(255, Math.floor((Date.now() - (soul.born || Date.now())) / 86400000));
    bytes[38] = days;

    var sum = crc16(bytes, 82);
    bytes[82] = (sum >> 8) & 0xFF;
    bytes[83] = sum & 0xFF;
    return bytes;
  }

  /** 3 bits por celda útil, en orden de lectura. */
  function toMatrix(bytes) {
    var matrix = new Uint8Array(GRID * GRID);
    for (var i = 0; i < bytes.length; i++) {
      for (var bit = 0; bit < 8; bit++) {
        var index = i * 8 + bit;
        if (index >= CELLS.length) break;
        var cell = CELLS[index];
        if (bytes[i] & (1 << (7 - bit))) {
          // Bit a 1: color 2..7 según posición; patrón determinista = "teselas orgánicas".
          matrix[cell.row * GRID + cell.col] = 2 + ((index * 5 + (index >> 3)) % 6);
        } else {
          matrix[cell.row * GRID + cell.col] = index % 7 === 0 ? 1 : 0;
        }
      }
    }
    ANCHORS.forEach(function (anchor) {
      for (var dr = 0; dr < 2; dr++) {
        for (var dc = 0; dc < 2; dc++) {
          matrix[(anchor.row + dr) * GRID + anchor.col + dc] = anchor.color;
        }
      }
    });
    return matrix;
  }

  function toBase64Url(bytes) {
    var binary = '';
    for (var i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
    window.btoa = window.btoa || function (value) { return value; };
    return window.btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }

  function fromBase64Url(code) {
    var normalized = String(code || '').trim().replace(/-/g, '+').replace(/_/g, '/');
    while (normalized.length % 4) normalized += '=';
    var binary;
    try { binary = window.atob(normalized); } catch (error) { return null; }
    var bytes = new Uint8Array(binary.length);
    for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes;
  }

  /** Alma completa -> { bytes, matrix, code } */
  function encode(soul) {
    var bytes = pack(soul);
    return { bytes: bytes, matrix: toMatrix(bytes), code: CODE_PREFIX + ':' + toBase64Url(bytes) };
  }

  function decodeName(bytes) {
    var name = '';
    for (var i = 1; i <= 12; i++) {
      if (!bytes[i]) break;
      name += String.fromCharCode(bytes[i]);
    }
    return name || 'Xolito';
  }

  /** Código de texto -> datos de alma (o null si el CRC no cuadra). */
  function decode(code) {
    if (typeof code !== 'string') return null;
    var stripped = code.trim();
    var colon = stripped.indexOf(':');
    if (colon >= 0) {
      var prefix = stripped.slice(0, colon).toUpperCase();
      if (prefix !== CODE_PREFIX) return null;
      stripped = stripped.slice(colon + 1);
    }
    var bytes = fromBase64Url(stripped);
    if (!bytes || bytes.length !== USABLE_BYTES) return null;
    if (bytes[0] !== SCHEMA) return null;
    var sum = crc16(bytes, 82);
    if (bytes[82] !== ((sum >> 8) & 0xFF) || bytes[83] !== (sum & 0xFF)) return null;

    var soulApi = global.YolcatlSoul;
    var species = (soulApi && soulApi.SPECIES[bytes[13]]) || { id: 'axolote' };
    var scene = (soulApi && soulApi.SCENES[bytes[24]]) || { id: 'chinampa' };
    var favoriteScene = (soulApi && soulApi.SCENES[bytes[32]]) || scene;
    var accessories = (soulApi && soulApi.ACCESSORIES) || [];
    var unlocked = [];
    var equipped = [];
    for (var a = 0; a < 6; a++) {
      var byte = bytes[25 + a];
      var item = accessories[a];
      if (!item) continue;
      if (byte & 1) unlocked.push(item.id);
      if (byte & 2) { equipped.push(item.id); if (unlocked.indexOf(item.id) < 0) unlocked.push(item.id); }
    }
    var favoriteAccessory = bytes[33] > 0 && accessories[bytes[33] - 1]
      ? accessories[bytes[33] - 1].id : '';

    return {
      name: decodeName(bytes),
      species: species.id,
      genes: {
        curiosity: bytes[14],
        social: bytes[15],
        vitality: bytes[16],
        appetite: bytes[17]
      },
      xp: reconstructXp(soulApi, bytes[18], bytes[19]),
      food: bytes[20],
      mood: bytes[21],
      energy: bytes[22],
      clean: bytes[23],
      scene: scene.id,
      unlocked: unlocked,
      equipped: equipped,
      memory: {
        affection: bytes[31],
        favoriteScene: favoriteScene.id,
        favoriteAccessory: favoriteAccessory,
        touches: bytes[34] * 5,
        plays: bytes[35] * 5,
        feeds: bytes[36] * 5,
        baths: bytes[37] * 5
      },
      born: Date.now() - bytes[38] * 86400000,
      lastSeen: Date.now()
    };
  }

  function reconstructXp(soulApi, level, into) {
    var ladder = [0, 40, 95, 165, 250];
    var base = ladder[Math.max(0, Math.min(4, level - 1))] || 0;
    return base + into;
  }

  // ---------------------------------------------------------------- dibujo

  function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  function glyph(ctx, x, y, unit, color) {
    // Emblema de especie (axolote) en pixel art de 7x5.
    var rows = ['.#...#.', '#######', '#.###.#', '#######', '.#...#.'];
    ctx.fillStyle = color;
    for (var r = 0; r < rows.length; r++) {
      for (var c = 0; c < rows[r].length; c++) {
        if (rows[r][c] === '#') ctx.fillRect(x + c * unit, y + r * unit, unit, unit);
      }
    }
  }

  /**
   * Dibuja el mosaico con marco ceremonial.
   * @param {HTMLCanvasElement} canvas
   * @param {Uint8Array} matrix 256 índices de paleta
   * @param {{caption?:string, species?:string, level?:number}} options
   */
  function render(canvas, matrix, options) {
    var opts = options || {};
    var size = canvas.width || 512;
    var ctx = canvas.getContext('2d');
    var margin = Math.round(size * 0.07);
    var inner = size - margin * 2;
    var cell = inner / GRID;

    ctx.clearRect(0, 0, size, size);
    ctx.imageSmoothingEnabled = false;

    // Marco
    ctx.fillStyle = PALETTE[0];
    ctx.fillRect(0, 0, size, size);
    ctx.fillStyle = PALETTE[1];
    ctx.fillRect(margin * 0.35, margin * 0.35, size - margin * 0.7, size - margin * 0.7);

    // Teselas de datos
    for (var row = 0; row < GRID; row++) {
      for (var col = 0; col < GRID; col++) {
        var colorIndex = matrix[row * GRID + col] % PALETTE.length;
        var x = margin + col * cell;
        var y = margin + row * cell;
        var inset = cell * 0.06;
        var radius = cell * ((row + col) % 3 === 0 ? 0.5 : 0.28);
        ctx.fillStyle = PALETTE[colorIndex];
        roundRect(ctx, x + inset, y + inset, cell - inset * 2, cell - inset * 2, radius);
        ctx.fill();
        // Brillo interno mínimo, sin tocar el centro óptico de la celda.
        ctx.fillStyle = 'rgba(255,255,255,0.22)';
        ctx.fillRect(x + inset * 2, y + inset * 2, cell * 0.14, cell * 0.14);
      }
    }

    // Anclas de calibración con doble marco
    ANCHORS.forEach(function (anchor) {
      var x = margin + anchor.col * cell;
      var y = margin + anchor.row * cell;
      ctx.strokeStyle = PALETTE[0];
      ctx.lineWidth = Math.max(1, cell * 0.08);
      ctx.strokeRect(x + 1, y + 1, cell * 2 - 2, cell * 2 - 2);
    });

    // Ornamento de esquinas del marco
    var unit = Math.max(2, Math.round(margin * 0.16));
    ctx.fillStyle = PALETTE[2];
    [[0, 0], [size - unit * 3, 0], [0, size - unit * 3], [size - unit * 3, size - unit * 3]]
      .forEach(function (corner) {
        for (var i = 0; i < 3; i++) {
          ctx.fillRect(corner[0] + i * unit, corner[1], unit, unit * 3);
          ctx.fillRect(corner[0], corner[1] + i * unit, unit * 3, unit);
        }
      });

    glyph(ctx, size - margin + unit, size - margin + unit, unit, PALETTE[2]);
    if (opts.caption) {
      ctx.fillStyle = PALETTE[1];
      ctx.font = 'bold ' + Math.round(size * 0.028) + 'px ui-monospace, monospace';
      ctx.textBaseline = 'top';
      ctx.textAlign = 'left';
      ctx.fillText(String(opts.caption).toUpperCase(), margin, size - margin + unit);
    }
  }

  global.YolcatlMosaic = {
    GRID: GRID,
    USABLE_BYTES: USABLE_BYTES,
    PALETTE: PALETTE,
    ANCHORS: ANCHORS,
    CELLS: CELLS,
    crc16: crc16,
    pack: pack,
    toMatrix: toMatrix,
    encode: encode,
    decode: decode,
    render: render
  };
})(window);
