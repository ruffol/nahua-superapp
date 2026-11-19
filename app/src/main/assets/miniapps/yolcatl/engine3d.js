/*
 * YOLCATL · engine3d.js
 * Motor vóxel propio (WebGL 1.0, sin librerías). Modelos definidos como matrices 3D de 16x16x16,
 * accesorios anclados a sockets y cuatro biomas con props. ~24 KiB empaquetado.
 *
 * Si el WebView no tiene WebGL, cae a un pintor 2D sobre el mismo canvas (mismo modelo).
 */
(function (global) {
  'use strict';

  var DIM = 16;
  var ORIGIN = 8; // centro del grid en x/z (centros de vóxel en x+0.5)

  // ---------------------------------------------------------------- matrices

  function mat4() {
    var m = new Float32Array(16);
    m[0] = m[5] = m[10] = m[15] = 1;
    return m;
  }

  function mul(a, b) {
    var out = new Float32Array(16);
    for (var c = 0; c < 4; c++) {
      for (var r = 0; r < 4; r++) {
        out[c * 4 + r] =
          a[r] * b[c * 4] +
          a[4 + r] * b[c * 4 + 1] +
          a[8 + r] * b[c * 4 + 2] +
          a[12 + r] * b[c * 4 + 3];
      }
    }
    return out;
  }

  function chain() {
    var m = mat4();
    for (var i = 0; i < arguments.length; i++) m = mul(m, arguments[i]);
    return m;
  }

  function translation(x, y, z) {
    var m = mat4();
    m[12] = x; m[13] = y; m[14] = z;
    return m;
  }

  function scaling(x, y, z) {
    var m = mat4();
    m[0] = x; m[5] = y; m[10] = z;
    return m;
  }

  function rotX(a) {
    var m = mat4(), c = Math.cos(a), s = Math.sin(a);
    m[5] = c; m[6] = s; m[9] = -s; m[10] = c;
    return m;
  }

  function rotY(a) {
    var m = mat4(), c = Math.cos(a), s = Math.sin(a);
    m[0] = c; m[2] = -s; m[8] = s; m[10] = c;
    return m;
  }

  function rotZ(a) {
    var m = mat4(), c = Math.cos(a), s = Math.sin(a);
    m[0] = c; m[1] = s; m[4] = -s; m[5] = c;
    return m;
  }

  function perspective(fovy, aspect, near, far) {
    var f = 1 / Math.tan(fovy / 2);
    var nf = 1 / (near - far);
    var m = new Float32Array(16);
    m[0] = f / aspect;
    m[5] = f;
    m[10] = (far + near) * nf;
    m[11] = -1;
    m[14] = 2 * far * near * nf;
    return m;
  }

  function lookAt(eye, center, up) {
    var zx = eye[0] - center[0], zy = eye[1] - center[1], zz = eye[2] - center[2];
    var len = Math.sqrt(zx * zx + zy * zy + zz * zz) || 1;
    zx /= len; zy /= len; zz /= len;

    var xx = up[1] * zz - up[2] * zy;
    var xy = up[2] * zx - up[0] * zz;
    var xz = up[0] * zy - up[1] * zx;
    len = Math.sqrt(xx * xx + xy * xy + xz * xz) || 1;
    xx /= len; xy /= len; xz /= len;

    var yx = zy * xz - zz * xy;
    var yy = zz * xx - zx * xz;
    var yz = zx * xy - zy * xx;

    var m = mat4();
    m[0] = xx; m[1] = yx; m[2] = zx;
    m[4] = xy; m[5] = yy; m[6] = zy;
    m[8] = xz; m[9] = yz; m[10] = zz;
    m[12] = -(xx * eye[0] + xy * eye[1] + xz * eye[2]);
    m[13] = -(yx * eye[0] + yy * eye[1] + yz * eye[2]);
    m[14] = -(zx * eye[0] + zy * eye[1] + zz * eye[2]);
    return m;
  }

  function transform(m, x, y, z) {
    return [
      m[0] * x + m[4] * y + m[8] * z + m[12],
      m[1] * x + m[5] * y + m[9] * z + m[13],
      m[2] * x + m[6] * y + m[10] * z + m[14]
    ];
  }

  function transform4(m, x, y, z) {
    return [
      m[0] * x + m[4] * y + m[8] * z + m[12],
      m[1] * x + m[5] * y + m[9] * z + m[13],
      m[2] * x + m[6] * y + m[10] * z + m[14],
      m[3] * x + m[7] * y + m[11] * z + m[15]
    ];
  }

  // ---------------------------------------------------------------- color

  function parseColor(hex) {
    var value = hex.replace('#', '');
    return [
      parseInt(value.slice(0, 2), 16) / 255,
      parseInt(value.slice(2, 4), 16) / 255,
      parseInt(value.slice(4, 6), 16) / 255
    ];
  }

  // ---------------------------------------------------------------- builder

  function Builder() { this.parts = Object.create(null); }

  Builder.prototype.part = function (name) {
    if (!this.parts[name]) this.parts[name] = Object.create(null);
    return this.parts[name];
  };

  Builder.prototype.put = function (name, x, y, z, c) {
    if (x < 0 || y < 0 || z < 0 || x >= DIM || y >= DIM || z >= DIM) return;
    this.part(name)[(x << 8) | (y << 4) | z] = c;
  };

  Builder.prototype.box = function (name, x0, x1, y0, y1, z0, z1, c) {
    for (var x = Math.round(x0); x <= Math.round(x1); x++) {
      for (var y = Math.round(y0); y <= Math.round(y1); y++) {
        for (var z = Math.round(z0); z <= Math.round(z1); z++) {
          this.put(name, x, y, z, c);
        }
      }
    }
  };

  /** Elipsoide con centro en coordenadas de centro de vóxel (x+0.5). */
  Builder.prototype.ell = function (name, cx, cy, cz, rx, ry, rz, c, opts) {
    var shell = opts && opts.shell;
    var yMin = opts && opts.yMin != null ? opts.yMin : 0;
    for (var x = 0; x < DIM; x++) {
      for (var y = yMin; y < DIM; y++) {
        for (var z = 0; z < DIM; z++) {
          var dx = (x + 0.5 - cx) / rx;
          var dy = (y + 0.5 - cy) / ry;
          var dz = (z + 0.5 - cz) / rz;
          var d = dx * dx + dy * dy + dz * dz;
          if (d <= 1 && (!shell || d > shell * shell)) this.put(name, x, y, z, c);
        }
      }
    }
  };

  /** Copia un constructor de prop al part indicado, desplazado (y opcionalmente girado 90°). */
  Builder.prototype.stamp = function (partName, source, dx, dy, dz, quarter) {
    var parts = source.parts;
    var target = this;
    Object.keys(parts).forEach(function (name) {
      var vox = parts[name];
      Object.keys(vox).forEach(function (key) {
        var code = Number(key);
        var x = (code >> 8) & 15;
        var y = (code >> 4) & 15;
        var z = code & 15;
        var nx = x, nz = z;
        if (quarter) {
          // Giro alrededor del centro del grid.
          nx = DIM - 1 - z;
          nz = x;
        }
        target.put(partName, nx + dx, y + dy, nz + dz, vox[key]);
      });
    });
  };

  // ---------------------------------------------------------------- mesher

  var FACES = [
    { dir: [1, 0, 0], corners: [[1, 0, 0], [1, 1, 0], [1, 1, 1], [1, 0, 1]], light: 0.84 },
    { dir: [-1, 0, 0], corners: [[0, 0, 1], [0, 1, 1], [0, 1, 0], [0, 0, 0]], light: 0.66 },
    { dir: [0, 1, 0], corners: [[0, 1, 0], [0, 1, 1], [1, 1, 1], [1, 1, 0]], light: 1.0 },
    { dir: [0, -1, 0], corners: [[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1]], light: 0.46 },
    { dir: [0, 0, 1], corners: [[0, 0, 1], [1, 0, 1], [1, 1, 1], [0, 1, 1]], light: 0.92 },
    { dir: [0, 0, -1], corners: [[1, 0, 0], [0, 0, 0], [0, 1, 0], [1, 1, 0]], light: 0.72 }
  ];

  function meshPart(vox, palette, originY) {
    var out = [];
    var oy = originY || 0;
    var keys = Object.keys(vox);
    for (var k = 0; k < keys.length; k++) {
      var code = Number(keys[k]);
      var x = (code >> 8) & 15;
      var y = (code >> 4) & 15;
      var z = code & 15;
      var color = palette[vox[keys[k]] % palette.length];
      for (var f = 0; f < FACES.length; f++) {
        var face = FACES[f];
        var nx = x + face.dir[0], ny = y + face.dir[1], nz = z + face.dir[2];
        if (nx >= 0 && ny >= 0 && nz >= 0 && nx < DIM && ny < DIM && nz < DIM) {
          if (vox[(nx << 8) | (ny << 4) | nz] !== undefined) continue;
        }
        var cr = color[0] * face.light;
        var cg = color[1] * face.light;
        var cb = color[2] * face.light;
        var quad = face.corners;
        var order = [0, 1, 2, 0, 2, 3];
        for (var q = 0; q < 6; q++) {
          var corner = quad[order[q]];
          out.push(
            x + corner[0] - ORIGIN,
            y + corner[1] - oy,
            z + corner[2] - ORIGIN,
            cr, cg, cb
          );
        }
      }
    }
    return out;
  }

  function withQuad(target, x0, z0, x1, z1, y, color, light) {
    // Quad horizontal (suelo) en coordenadas de mundo.
    var r = color[0] * light, g = color[1] * light, b = color[2] * light;
    var tris = [
      [x0, y, z0], [x1, y, z0], [x1, y, z1],
      [x0, y, z0], [x1, y, z1], [x0, y, z1]
    ];
    for (var i = 0; i < tris.length; i++) {
      target.push(tris[i][0], tris[i][1], tris[i][2], r, g, b);
    }
  }

  // ---------------------------------------------------------------- especies

  var PALETTES = {
    axolote: ['#FFB3C7', '#FFDCE6', '#FF7A9C', '#FF9EB8', '#3A2430', '#FFFFFF'],
    dorado: ['#F2BE58', '#FFE7B0', '#FF8A5C', '#FFB26B', '#3A2430', '#FFFFFF'],
    tortuga: ['#4F7340', '#8E7A45', '#6E8F5E', '#3C2E1E', '#22301E', '#D8C48A'],
    xolo: ['#8C8B8F', '#B7B5BA', '#6E6D72', '#4A4750', '#1B1720', '#FFFFFF'],
    colibri: ['#1E9E6A', '#8FE3B6', '#2F6BFF', '#F4B32B', '#1B1720', '#FFFFFF']
  };

  // Índices de paleta: 0 cuerpo, 1 panza, 2 branquia/acento, 3 aleta, 4 ojos, 5 brillo.

  function buildAxolote(palette) {
    var b = new Builder();
    // torso
    b.ell('body', 8, 4.6, 6.4, 3.4, 3.0, 4.2, 0);
    // cabeza
    b.ell('body', 8, 5.0, 11.6, 4.2, 3.4, 2.4, 0);
    // hocico
    b.box('body', 6, 9, 3, 4, 13, 14, 0);
    // panza
    b.ell('body', 8, 3.0, 6.6, 2.7, 1.7, 3.4, 1);
    // ojos
    b.box('body', 5, 5, 5, 6, 13, 13, 4);
    b.box('body', 10, 10, 5, 6, 13, 13, 4);
    b.put('body', 5, 6, 13, 5);
    b.put('body', 10, 6, 13, 5);
    // boca
    b.box('body', 7, 8, 2, 2, 14, 14, 4);
    // patas
    b.box('body', 4, 5, 0, 1, 9, 10, 0);
    b.box('body', 10, 11, 0, 1, 9, 10, 0);
    b.box('body', 4, 5, 0, 1, 3, 4, 0);
    b.box('body', 10, 11, 0, 1, 3, 4, 0);

    // branquias: tres frondas por lado
    b.box('gillL', 12, 14, 6, 6, 11, 12, 2);
    b.box('gillL', 12, 14, 4, 5, 10, 10, 2);
    b.box('gillL', 12, 13, 2, 3, 9, 9, 2);
    b.box('gillR', 1, 3, 6, 6, 11, 12, 2);
    b.box('gillR', 1, 3, 4, 5, 10, 10, 2);
    b.box('gillR', 2, 3, 2, 3, 9, 9, 2);

    // cola
    b.ell('tail', 8, 4.2, 1.6, 1.8, 1.9, 2.2, 0);
    b.ell('tail', 8, 4.8, 0.4, 1.1, 2.2, 1.8, 3);

    return b;
  }

  function buildTortuga() {
    var b = new Builder();
    b.ell('body', 8, 4.0, 6.6, 4.2, 2.9, 4.4, 0);
    b.ell('body', 8, 2.2, 6.6, 3.8, 1.2, 4.0, 5);     // plastrón
    b.ell('body', 8, 3.0, 6.6, 3.6, 2.2, 3.8, 1, { shell: 0.92 }); // dibujo del caparazón
    b.ell('body', 8, 5.2, 11.8, 1.9, 1.7, 1.9, 2);    // cabeza
    b.box('body', 8, 8, 6, 6, 12, 13, 4);              // ojos
    b.box('body', 7, 7, 6, 6, 12, 13, 4);
    b.box('body', 8, 8, 5, 5, 13, 13, 3);              // pico
    // aletas
    b.box('legs', 2, 4, 1, 1, 8, 11, 2);
    b.box('legs', 11, 13, 1, 1, 8, 11, 2);
    b.box('legs', 3, 4, 1, 1, 3, 6, 2);
    b.box('legs', 11, 12, 1, 1, 3, 6, 2);
    b.box('tail', 8, 8, 3, 3, 1, 2, 2);
    return b;
  }

  function buildXolo() {
    var b = new Builder();
    b.ell('body', 8, 4.4, 6.0, 3.2, 2.8, 4.6, 0);
    b.ell('body', 8, 3.2, 6.4, 2.4, 1.5, 3.6, 1);
    b.ell('body', 8, 5.6, 12.0, 3.2, 2.9, 2.6, 0);   // cabeza grande de cachorro
    b.ell('body', 8, 4.8, 13.6, 1.8, 1.5, 1.2, 1);   // hocico
    b.box('body', 8, 8, 6, 6, 14, 14, 3);
    b.box('body', 5, 5, 6, 7, 13, 13, 4);
    b.box('body', 10, 10, 6, 7, 13, 13, 4);
    b.put('body', 5, 7, 13, 5);
    b.put('body', 10, 7, 13, 5);
    // orejas
    b.box('body', 4, 5, 8, 10, 11, 12, 0);
    b.box('body', 10, 11, 8, 10, 11, 12, 0);
    // patas
    b.box('body', 4, 5, 0, 2, 8, 9, 0);
    b.box('body', 10, 11, 0, 2, 8, 9, 0);
    b.box('body', 4, 5, 0, 2, 3, 4, 0);
    b.box('body', 10, 11, 0, 2, 3, 4, 0);
    // cola
    b.box('tail', 7, 8, 5, 6, 1, 2, 0);
    b.box('tail', 7, 8, 7, 7, 0, 1, 0);
    return b;
  }

  function buildColibri() {
    var b = new Builder();
    b.ell('body', 8, 5.0, 8.0, 2.6, 2.6, 3.4, 0);
    b.ell('body', 8, 4.2, 8.4, 1.8, 1.6, 2.4, 1);
    b.ell('body', 8, 7.2, 11.0, 2.0, 2.0, 2.0, 0);
    b.box('body', 8, 8, 7, 7, 13, 15, 3);   // pico largo
    b.box('body', 7, 7, 8, 8, 12, 12, 4);
    b.box('body', 9, 9, 8, 8, 12, 12, 4);
    b.box('gillL', 11, 14, 5, 5, 7, 10, 2);  // alas
    b.box('gillR', 1, 4, 5, 5, 7, 10, 2);
    b.box('tail', 6, 9, 4, 4, 3, 5, 0);
    return b;
  }

  var SPECIES = {
    axolote: { palette: PALETTES.axolote, build: buildAxolote, scale: 1.0, focus: 4.8, radius: 9.6, accScale: 1.0,
      sockets: { head: [0, 8.2, 1.6], face: [0, 4.4, 5.2], neck: [0, 5.2, -1.6], back: [0, 6.4, -3.0], floor: [8, 0, 5] } },
    dorado: { palette: PALETTES.dorado, build: buildAxolote, scale: 1.0, focus: 4.8, radius: 9.6, accScale: 1.0,
      sockets: { head: [0, 8.2, 1.6], face: [0, 4.4, 5.2], neck: [0, 5.2, -1.6], back: [0, 6.4, -3.0], floor: [8, 0, 5] } },
    tortuga: { palette: PALETTES.tortuga, build: buildTortuga, scale: 1.0, focus: 4.0, radius: 8.6, accScale: 0.62,
      sockets: { head: [0, 6.6, 3.4], face: [0, 6.2, 4.2], neck: [0, 5.2, 2.8], back: [0, 6.8, -1.0], floor: [8, 0, 5] } },
    xolo: { palette: PALETTES.xolo, build: buildXolo, scale: 1.0, focus: 5.0, radius: 9.2, accScale: 0.95,
      sockets: { head: [0, 8.4, 3.8], face: [0, 5.8, 6.0], neck: [0, 6.0, 2.0], back: [0, 6.6, -1.6], floor: [8, 0, 5] } },
    colibri: { palette: PALETTES.colibri, build: buildColibri, scale: 1.15, focus: 5.6, radius: 8.2, accScale: 0.6,
      sockets: { head: [0, 8.8, 3.0], face: [0, 6.4, 6.2], neck: [0, 6.4, 1.6], back: [0, 5.8, -2.2], floor: [8, 0, 5] } }
  };

  // ---------------------------------------------------------------- accesorios

  var ACCESSORIES = {
    sombrero: { palette: ['#D8B25A', '#A97C2E', '#7A4A22'], anchor: 'head', lift: 0, build: function () {
      var b = new Builder();
      b.ell('acc', 8, 6.4, 8, 3.8, 0.45, 3.8, 0);
      b.ell('acc', 8, 7.4, 8, 2.0, 1.3, 2.0, 0);
      b.ell('acc', 8, 6.7, 8, 2.2, 0.45, 2.2, 2);
      return b;
    }, pivot: [0, 6.2, 0] },
    corona: { palette: ['#FF5B4A', '#F4B32B', '#A259E6', '#1E9E6A'], anchor: 'head', build: function () {
      var b = new Builder();
      for (var i = 0; i < 8; i++) {
        var angle = (i / 8) * Math.PI * 2;
        var x = Math.round(8 + Math.cos(angle) * 3.4 - 0.5);
        var z = Math.round(8 + Math.sin(angle) * 3.4 - 0.5);
        b.put('acc', x, 7, z, i % 4);
        b.put('acc', x, 8, z, (i + 1) % 4);
      }
      return b;
    }, pivot: [0, 7.0, 0] },
    gafas: { palette: ['#121020', '#2F6BFF'], anchor: 'face', build: function () {
      var b = new Builder();
      b.box('acc', 4, 11, 5, 5, 11, 11, 0);
      b.box('acc', 4, 5, 4, 6, 11, 11, 0);
      b.box('acc', 10, 11, 4, 6, 11, 11, 0);
      b.box('acc', 4, 5, 5, 5, 12, 12, 1);
      b.box('acc', 10, 11, 5, 5, 12, 12, 1);
      return b;
    }, pivot: [0, 5.0, 3.0] },
    bufanda: { palette: ['#FF5B4A', '#F4B32B'], anchor: 'neck', build: function () {
      var b = new Builder();
      b.ell('acc', 8, 5.4, 8, 3.2, 0.9, 2.6, 0, { shell: 0.55 });
      b.box('acc', 4, 5, 3, 5, 9, 10, 0);
      b.box('acc', 4, 5, 2, 2, 9, 9, 1);
      return b;
    }, pivot: [0, 5.0, 0] },
    mochila: { palette: ['#1E9E6A', '#F4B32B', '#7A4A22'], anchor: 'back', build: function () {
      var b = new Builder();
      b.box('acc', 5, 10, 4, 7, 3, 5, 0);
      b.box('acc', 5, 10, 6, 6, 3, 5, 1);
      b.box('acc', 6, 9, 3, 3, 3, 5, 2);
      return b;
    }, pivot: [0, 4.0, -3.0] },
    pelota: { palette: ['#FF5B4A', '#F6EEDC', '#F4B32B'], anchor: 'floor', build: function () {
      var b = new Builder();
      b.ell('acc', 8, 8.6, 8, 1.7, 1.7, 1.7, 0);
      b.box('acc', 6, 9, 8, 8, 7, 8, 1);
      b.box('acc', 8, 8, 7, 9, 6, 9, 2);
      return b;
    }, pivot: [0, 8.0, 0] }
  };

  // ---------------------------------------------------------------- biomas

  /** Prop con paleta propia, colocado en coordenadas de mundo. */
  function propOf(palette, build, dx, dz, q) {
    return { palette: palette.map(parseColor), build: build, dx: dx, dz: dz, q: q ? 1 : 0 };
  }

  function padProp() {
    var b = new Builder();
    b.ell('p', 8, 1.4, 8, 3.0, 0.4, 3.0, 0);
    b.ell('p', 8, 1.5, 8, 2.0, 0.3, 2.0, 1, { shell: 0.9 });
    b.box('p', 8, 8, 2, 2, 8, 8, 2);
    return b;
  }

  function reedProp() {
    var b = new Builder();
    b.box('p', 8, 8, 0, 5, 8, 8, 0);
    b.box('p', 7, 7, 3, 4, 8, 8, 1);
    b.box('p', 8, 8, 6, 6, 8, 8, 2);
    return b;
  }

  function rockProp() {
    var b = new Builder();
    b.ell('p', 8, 1.5, 8, 2.3, 1.5, 1.9, 0);
    b.ell('p', 8, 2.6, 8, 1.2, 0.6, 1.0, 1);
    return b;
  }

  function mushroomProp() {
    var b = new Builder();
    b.box('p', 8, 8, 0, 2, 8, 8, 0);
    b.ell('p', 8, 3.4, 8, 1.6, 0.9, 1.6, 1);
    b.box('p', 8, 8, 3, 3, 8, 8, 2);
    return b;
  }

  function palmProp() {
    var b = new Builder();
    b.box('p', 8, 8, 0, 5, 8, 8, 0);
    b.box('p', 8, 8, 6, 6, 8, 8, 0);
    b.ell('p', 8, 6.6, 8, 3.4, 0.4, 3.2, 1);
    b.box('p', 5, 5, 6, 6, 5, 7, 1);
    b.box('p', 11, 11, 6, 6, 9, 11, 1);
    return b;
  }

  function shellProp() {
    var b = new Builder();
    b.ell('p', 8, 0.9, 8, 1.7, 0.9, 1.5, 0);
    b.box('p', 7, 9, 1, 1, 7, 8, 1);
    return b;
  }

  function rugProp() {
    var b = new Builder();
    for (var x = 2; x <= 13; x++) {
      for (var z = 2; z <= 13; z++) {
        var ring = (x === 2 || x === 13 || z === 2 || z === 13);
        var inner = (x >= 6 && x <= 9 && z >= 6 && z <= 9);
        b.put('p', x, 0, z, ring ? 1 : (inner ? 2 : 0));
      }
    }
    return b;
  }

  function lampProp() {
    var b = new Builder();
    b.box('p', 8, 8, 0, 4, 8, 8, 0);
    b.box('p', 6, 10, 5, 6, 6, 10, 1);
    return b;
  }

  function pillowProp() {
    var b = new Builder();
    b.box('p', 6, 10, 0, 1, 6, 10, 0);
    b.box('p', 7, 9, 1, 1, 7, 9, 1);
    return b;
  }

  function ballProp() {
    var b = new Builder();
    b.ell('p', 8, 1.4, 8, 1.5, 1.3, 1.5, 0);
    b.box('p', 7, 9, 1, 2, 6, 7, 1);
    return b;
  }

  var SCENES = {
    chinampa: {
      label: 'Chinampa',
      floor: '#BFAE83',
      particle: { color: 'rgba(214,255,240,0.55)', kind: 'bubble', count: 18 },
      props: function () {
        return [
          propOf(['#4F8B4A', '#2F5E2F', '#FF7A9C'], padProp, -10, 3),
          propOf(['#4F8B4A', '#2F5E2F', '#F4B32B'], padProp, 9, -6, 1),
          propOf(['#4F8B4A', '#2F5E2F', '#FF7A9C'], padProp, -4, 11, 1),
          propOf(['#6E8F5E', '#4F7A44', '#C9A24A'], reedProp, -8, -7),
          propOf(['#6E8F5E', '#4F7A44', '#C9A24A'], reedProp, 7, -9),
          propOf(['#6C7F94', '#55667A', '#8E9DAE'], rockProp, 8, 4),
          propOf(['#6C7F94', '#55667A', '#8E9DAE'], rockProp, -7, 7, 1)
        ];
      }
    },
    cenote: {
      label: 'Cenote',
      floor: '#2B2F3F',
      particle: { color: 'rgba(140,240,255,0.75)', kind: 'spark', count: 22 },
      props: function () {
        return [
          propOf(['#4A4E63', '#5D6280', '#7C82A8'], rockProp, -9, 3),
          propOf(['#4A4E63', '#5D6280', '#7C82A8'], rockProp, 8, -6, 1),
          propOf(['#4A4E63', '#5D6280', '#7C82A8'], rockProp, 10, 5),
          propOf(['#8C7CFF', '#B6A8FF', '#E8FFFB'], mushroomProp, -5, 8),
          propOf(['#8C7CFF', '#B6A8FF', '#E8FFFB'], mushroomProp, 4, 9, 1),
          propOf(['#5BE7C0', '#9CFFE0', '#E8FFFB'], mushroomProp, -9, -8)
        ];
      }
    },
    playa: {
      label: 'Estero',
      floor: '#E2CEA0',
      particle: { color: 'rgba(255,255,255,0.5)', kind: 'bubble', count: 14 },
      props: function () {
        return [
          propOf(['#A97C2E', '#4F8B4A', '#6E8F5E'], palmProp, -10, -7),
          propOf(['#E8D3A9', '#C94F74', '#F6EEDC'], shellProp, 8, 7),
          propOf(['#E8D3A9', '#F4B32B', '#F6EEDC'], shellProp, -6, 9, 1),
          propOf(['#6C7F94', '#8E9DAE', '#55667A'], rockProp, 10, -4)
        ];
      }
    },
    cuarto: {
      label: 'Cuarto',
      floor: '#8C5B3E',
      particle: { color: 'rgba(255,214,150,0.45)', kind: 'dust', count: 12 },
      props: function () {
        return [
          propOf(['#C94F74', '#F4B32B', '#F6EEDC'], rugProp, 0, 0),
          propOf(['#6C7F94', '#F4B32B', '#F6EEDC'], lampProp, 10, -8),
          propOf(['#F6EEDC', '#E7DCC4', '#C94F74'], pillowProp, -9, 6),
          propOf(['#FF5B4A', '#F6EEDC', '#F4B32B'], ballProp, 9, 5)
        ];
      }
    }
  };

  // ---------------------------------------------------------------- webgl

  var VERT = [
    'attribute vec3 aPos;',
    'attribute vec3 aColor;',
    'uniform mat4 uMVP;',
    'varying vec3 vColor;',
    'void main() {',
    '  vColor = aColor;',
    '  gl_Position = uMVP * vec4(aPos, 1.0);',
    '}'
  ].join('\n');

  var FRAG = [
    'precision mediump float;',
    'varying vec3 vColor;',
    'void main() { gl_FragColor = vec4(vColor, 1.0); }'
  ].join('\n');

  function shader(gl, type, source) {
    var sh = gl.createShader(type);
    gl.shaderSource(sh, source);
    gl.compileShader(sh);
    if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) {
      throw new Error('shader: ' + gl.getShaderInfoLog(sh));
    }
    return sh;
  }

  function program(gl) {
    var p = gl.createProgram();
    gl.attachShader(p, shader(gl, gl.VERTEX_SHADER, VERT));
    gl.attachShader(p, shader(gl, gl.FRAGMENT_SHADER, FRAG));
    gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) {
      throw new Error('link: ' + gl.getProgramInfoLog(p));
    }
    return p;
  }

  function upload(gl, data) {
    if (!gl) return { buffer: null, count: data.length / 6 };
    var buffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(data), gl.STATIC_DRAW);
    return { buffer: buffer, count: data.length / 6 };
  }

  function buildSpeciesMesh(gl, id) {
    var spec = SPECIES[id] || SPECIES.axolote;
    var palette = spec.palette.map(parseColor);
    var builder = spec.build();
    var meshes = {};
    var raw = {};
    Object.keys(builder.parts).forEach(function (name) {
      var data = meshPart(builder.parts[name], palette);
      raw[name] = data;
      meshes[name] = upload(gl, data);
    });
    return {
      meshes: meshes,
      raw: raw,
      sockets: spec.sockets,
      scale: spec.scale,
      focus: spec.focus,
      radius: spec.radius,
      accScale: spec.accScale
    };
  }

  function buildAccessoryMesh(gl, id) {
    var def = ACCESSORIES[id];
    if (!def) return null;
    var palette = def.palette.map(parseColor);
    var builder = def.build();
    var vox = builder.parts.acc || {};
    return {
      mesh: upload(gl, meshPart(vox, palette, def.pivot[1])),
      anchor: def.anchor,
      pivot: def.pivot
    };
  }

  function buildSceneMesh(gl, id) {
    var scene = SCENES[id] || SCENES.chinampa;
    var data = [];
    scene.props().forEach(function (prop) {
      var builder = prop.build();
      Object.keys(builder.parts).forEach(function (name) {
        var part = meshPart(builder.parts[name], prop.palette);
        for (var i = 0; i < part.length; i += 6) {
          var x = part[i], z = part[i + 2];
          if (prop.q) { part[i] = -z; part[i + 2] = x; }
          part[i] += prop.dx;
          part[i + 2] += prop.dz;
        }
        for (var j = 0; j < part.length; j++) data.push(part[j]);
      });
    });
    withQuad(data, -34, -34, 34, 34, -0.05, parseColor(scene.floor), 0.92);
    return { mesh: upload(gl, data), scene: scene };
  }

  // ---------------------------------------------------------------- renderer

  function Engine(canvas, overlay) {
    this.canvas = canvas;
    this.overlay = overlay;
    this.gl = null;
    this.ctx2d = null;
    this.pixels = [];
    this.behavior = 'observe';
    this.species = 'axolote';
    this.sceneId = 'chinampa';
    this.accessories = [];
    this.time = 0;
    this.last = 0;
    this.running = false;
    this.stats = 0;
    this.faces = [];
    this.init();
  }

  Engine.prototype.init = function () {
    try {
      this.gl = this.canvas.getContext('webgl', {
        alpha: true, antialias: false, depth: true, premultipliedAlpha: false
      }) || this.canvas.getContext('experimental-webgl');
    } catch (error) {
      this.gl = null;
    }
    if (this.gl) {
      try {
        var gl = this.gl;
        this.prog = program(gl);
        this.aPos = gl.getAttribLocation(this.prog, 'aPos');
        this.aColor = gl.getAttribLocation(this.prog, 'aColor');
        this.uMVP = gl.getUniformLocation(this.prog, 'uMVP');
        gl.enable(gl.DEPTH_TEST);
        gl.disable(gl.CULL_FACE);
        gl.clearColor(0, 0, 0, 0);
      } catch (error) {
        this.gl = null;
        this.ctx2d = this.canvas.getContext('2d');
      }
    } else {
      this.ctx2d = this.canvas.getContext('2d');
    }
    this.speciesMesh = buildSpeciesMesh(this.gl, this.species);
    this.sceneMesh = buildSceneMesh(this.gl, this.sceneId);
    this.accent = this.buildAccentMeshes();
    this.resize();
  };

  Engine.prototype.buildAccentMeshes = function () {
    var self = this;
    var meshes = {};
    this.accessories.forEach(function (id) {
      if (meshes[id] === undefined) meshes[id] = buildAccessoryMesh(self.gl, id);
    });
    return meshes;
  };

  Engine.prototype.resize = function () {
    var ratio = Math.min(2, global.devicePixelRatio || 1);
    var rect = this.canvas.getBoundingClientRect();
    var width = Math.max(160, Math.round(rect.width * ratio));
    var height = Math.max(120, Math.round(rect.height * ratio));
    if (this.canvas.width !== width || this.canvas.height !== height) {
      this.canvas.width = width;
      this.canvas.height = height;
    }
    if (this.overlay) {
      this.overlay.width = width;
      this.overlay.height = height;
    }
    if (this.gl) this.gl.viewport(0, 0, width, height);
    this.spawnParticles();
  };

  Engine.prototype.spawnParticles = function () {
    var def = (SCENES[this.sceneId] || SCENES.chinampa).particle;
    var width = this.overlay ? this.overlay.width : 300;
    var height = this.overlay ? this.overlay.height : 200;
    this.pixels = [];
    for (var i = 0; i < def.count; i++) {
      this.pixels.push({
        x: Math.random() * width,
        y: Math.random() * height,
        r: 1.5 + Math.random() * 2.5,
        speed: 4 + Math.random() * 12,
        phase: Math.random() * Math.PI * 2
      });
    }
    this.particleStyle = def;
  };

  Engine.prototype.setSpecies = function (id) {
    if (!SPECIES[id] || id === this.species) return;
    this.species = id;
    this.speciesMesh = buildSpeciesMesh(this.gl, id);
  };

  Engine.prototype.setScene = function (id) {
    if (!SCENES[id] || id === this.sceneId) return;
    this.sceneId = id;
    this.sceneMesh = buildSceneMesh(this.gl, id);
    this.spawnParticles();
  };

  Engine.prototype.setAccessories = function (list) {
    this.accessories = list.slice();
    this.accent = this.buildAccentMeshes();
  };

  Engine.prototype.setBehavior = function (behavior) {
    this.behavior = behavior || 'observe';
  };

  Engine.prototype.start = function () {
    if (this.running) return;
    this.running = true;
    this.last = 0;
    var self = this;
    var frame = function (now) {
      if (!self.running) return;
      self.render(now);
      global.requestAnimationFrame(frame);
    };
    global.requestAnimationFrame(frame);
  };

  Engine.prototype.stop = function () { this.running = false; };

  Engine.prototype.pose = function (behavior, t) {
    var bob = Math.sin(t * 1.7) * 0.35;
    var yaw = Math.sin(t * 0.42) * 0.18;
    var roll = 0;
    var pitch = 0;
    var breathRate = 2.2;
    var breathAmp = 0.035;
    var gillAmp = 0.22;
    var tailAmp = 0.28;

    if (behavior === 'sleep') {
      bob = -0.5 + Math.sin(t * 0.9) * 0.12;
      roll = 0.10;
      breathRate = 0.9;
      breathAmp = 0.055;
      gillAmp = 0.05;
      tailAmp = 0.05;
    } else if (behavior === 'play') {
      bob = Math.abs(Math.sin(t * 3.1)) * 1.3;
      yaw = Math.sin(t * 2.2) * 0.4;
      roll = Math.sin(t * 2.2) * 0.12;
      gillAmp = 0.4;
      tailAmp = 0.55;
    } else if (behavior === 'greetUser') {
      bob = Math.abs(Math.sin(t * 2.6)) * 0.9;
      yaw = Math.sin(t * 1.4) * 0.22;
      gillAmp = 0.35;
      tailAmp = 0.4;
    } else if (behavior === 'seekFood') {
      bob = -1.2 + Math.sin(t * 1.2) * 0.3;
      pitch = 0.12;
      yaw = Math.sin(t * 0.9) * 0.5;
      tailAmp = 0.4;
    } else if (behavior === 'cleanUp') {
      yaw = t * 1.1;
      bob = Math.sin(t * 3.0) * 0.25;
      gillAmp = 0.35;
    } else if (behavior === 'pose') {
      yaw = 0.32 + Math.sin(t * 0.8) * 0.15;
      bob = Math.sin(t * 1.1) * 0.2;
      gillAmp = 0.3;
    } else if (behavior === 'observe') {
      yaw = Math.sin(t * 0.28) * 0.22;
    } else if (behavior === 'feed' || behavior === 'play') {
      bob = Math.abs(Math.sin(t * 3.4)) * 0.8;
    }

    return {
      bob: bob, yaw: yaw, roll: roll, pitch: pitch,
      breath: 1 + Math.sin(t * breathRate) * breathAmp,
      gill: Math.sin(t * 2.4) * gillAmp,
      tail: Math.sin(t * 2.9) * tailAmp
    };
  };

  Engine.prototype.render = function (now) {
    var delta = this.last ? Math.min(0.05, (now - this.last) / 1000) : 0.016;
    this.last = now;
    this.time += delta;
    var t = this.time;
    var pose = this.pose(this.behavior, t);

    var width = this.canvas.width;
    var height = this.canvas.height;
    var aspect = width / Math.max(1, height);

    var base = chain(
      translation(Math.sin(t * 0.8) * 0.25, pose.bob, 0),
      rotY(pose.yaw),
      rotZ(pose.roll),
      rotX(pose.pitch),
      scaling(this.speciesMesh.scale, this.speciesMesh.scale, this.speciesMesh.scale)
    );

    var bodyMatrix = chain(base, translation(0, 0, 0), scaling(1, pose.breath, 1));
    var parts = this.speciesMesh.meshes;

    var matrices = {};
    matrices.body = bodyMatrix;
    matrices.gillL = chain(base, translation(-4.4, 4.4, 2.4), rotZ(pose.gill), translation(4.4, -4.4, -2.4));
    matrices.gillR = chain(base, translation(4.4, 4.4, 2.4), rotZ(-pose.gill), translation(-4.4, -4.4, -2.4));
    matrices.tail = chain(base, translation(0, 4.2, -4.4), rotY(pose.tail), translation(0, -4.2, 4.4));
    matrices.legs = base;

    // Cámara que enmarca al animal completo (radio y foco por especie).
    var radius = this.speciesMesh.radius || 9.5;
    var fov = 32 * Math.PI / 180;
    var distance = radius / Math.sin(fov / 2) * 1.04;
    var azimuth = 0.55 + Math.sin(t * 0.13) * 0.1;
    var elevation = 0.22;
    var target = [0, this.speciesMesh.focus || 4.6, 0];
    var eye = [
      target[0] + Math.sin(azimuth) * Math.cos(elevation) * distance,
      target[1] + Math.sin(elevation) * distance,
      target[2] + Math.cos(azimuth) * Math.cos(elevation) * distance
    ];
    var view = lookAt(eye, target, [0, 1, 0]);
    var proj = perspective(fov, aspect, 0.5, 200);
    var vp = mul(proj, view);

    if (this.gl) {
      this.drawGL(vp, parts, matrices, base);
    } else {
      this.draw2D(vp, parts, matrices);
    }
    this.drawParticles(t, delta);
    this.stats++;
  };

  Engine.prototype.drawGL = function (vp, parts, matrices, base) {
    var gl = this.gl;
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
    gl.useProgram(this.prog);

    // Escena (suelo + props) sin transformación.
    this.drawMesh(this.sceneMesh.mesh, vp);

    var names = Object.keys(parts);
    for (var i = 0; i < names.length; i++) {
      var name = names[i];
      var matrix = matrices[name] || base;
      this.drawMesh(parts[name], mul(vp, matrix));
    }

    var sockets = this.speciesMesh.sockets;
    for (var a = 0; a < this.accessories.length; a++) {
      var acc = this.accent[this.accessories[a]];
      if (!acc) continue;
      var socket = sockets[acc.anchor] || sockets.head;
      var fit = this.speciesMesh.accScale || 1;
      var accMatrix = chain(
        base,
        translation(socket[0], socket[1], socket[2]),
        scaling(fit, fit, fit)
      );
      this.drawMesh(acc.mesh, mul(vp, accMatrix));
    }
  };

  Engine.prototype.drawMesh = function (mesh, matrix) {
    if (!mesh || !mesh.count) return;
    var gl = this.gl;
    gl.uniformMatrix4fv(this.uMVP, false, matrix);
    gl.bindBuffer(gl.ARRAY_BUFFER, mesh.buffer);
    gl.enableVertexAttribArray(this.aPos);
    gl.vertexAttribPointer(this.aPos, 3, gl.FLOAT, false, 24, 0);
    gl.enableVertexAttribArray(this.aColor);
    gl.vertexAttribPointer(this.aColor, 3, gl.FLOAT, false, 24, 12);
    gl.drawArrays(gl.TRIANGLES, 0, mesh.count);
  };

  /** Respaldo sin WebGL: pinta las caras ordenadas por profundidad (pintor). */
  Engine.prototype.draw2D = function (vp, parts, matrices) {
    var ctx = this.ctx2d;
    var width = this.canvas.width, height = this.canvas.height;
    ctx.clearRect(0, 0, width, height);
    var faces = [];
    var names = Object.keys(matrices);
    for (var n = 0; n < names.length; n++) {
      var data = this.speciesMesh.raw[names[n]];
      if (!data) continue;
      var m = mul(vp, matrices[names[n]]);
      for (var i = 0; i < data.length; i += 18) {
        var pts = [];
        var depth = 0;
        for (var v = 0; v < 3; v++) {
          var idx = i + v * 6;
          var p = transform4(m, data[idx], data[idx + 1], data[idx + 2]);
          pts.push(p);
          depth += p[3];
        }
        faces.push({
          pts: pts,
          depth: depth / 3,
          color: 'rgb(' + Math.round(data[i + 3] * 255) + ',' +
            Math.round(data[i + 4] * 255) + ',' + Math.round(data[i + 5] * 255) + ')'
        });
      }
    }
    faces.sort(function (a, b) { return b.depth - a.depth; });
    for (var f = 0; f < faces.length; f++) {
      var face = faces[f];
      ctx.beginPath();
      for (var k = 0; k < 3; k++) {
        var pt = face.pts[k];
        var w = pt[3] || 0.0001;
        var sx = ((pt[0] / w) * 0.5 + 0.5) * width;
        var sy = (0.5 - (pt[1] / w) * 0.5) * height;
        if (k === 0) ctx.moveTo(sx, sy); else ctx.lineTo(sx, sy);
      }
      ctx.closePath();
      ctx.fillStyle = face.color;
      ctx.strokeStyle = face.color;
      ctx.lineWidth = 1;
      ctx.fill();
      ctx.stroke();
    }
  };

  Engine.prototype.drawParticles = function (t, delta) {
    var canvas = this.overlay;
    if (!canvas) return;
    var ctx = canvas.getContext('2d');
    var width = canvas.width, height = canvas.height;
    ctx.clearRect(0, 0, width, height);
    var style = this.particleStyle || { color: 'rgba(255,255,255,0.5)', kind: 'dust', count: 0 };
    ctx.fillStyle = style.color;
    for (var i = 0; i < this.pixels.length; i++) {
      var p = this.pixels[i];
      if (style.kind === 'spark') {
        p.x += Math.sin(t * 0.7 + p.phase) * 8 * delta;
        p.y -= p.speed * 0.25 * delta;
      } else if (style.kind === 'dust') {
        p.x += Math.sin(t * 0.3 + p.phase) * 10 * delta;
        p.y += Math.cos(t * 0.4 + p.phase) * 8 * delta;
      } else {
        p.y -= p.speed * delta * 4;
        p.x += Math.sin(t + p.phase) * 6 * delta;
      }
      if (p.y < -6) { p.y = height + 6; p.x = Math.random() * width; }
      if (p.y > height + 6) p.y = -6;
      if (p.x < -6) p.x = width + 6;
      if (p.x > width + 6) p.x = -6;
      if (style.kind === 'spark') {
        ctx.fillRect(p.x, p.y, p.r, p.r);
      } else {
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        ctx.fill();
      }
    }
  };

  global.YolcatlEngine = {
    create: function (canvas, overlay) {
      var engine = new Engine(canvas, overlay);
      engine.start();
      return engine;
    },
    SPECIES: SPECIES,
    SCENES: SCENES,
    ACCESSORIES: ACCESSORIES,
    Builder: Builder
  };
})(window);
