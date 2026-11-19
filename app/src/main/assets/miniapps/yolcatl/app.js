/* YOLCATL · app.js — pinta la interfaz pixel art y cablea alma + motor 3D. */
(function () {
  'use strict';

  var soulApi = window.YolcatlSoul;
  var mosaic = window.YolcatlMosaic;
  var loaded = soulApi.load();
  var soul = loaded.soul;
  var engine = null;
  var toastTimer = null;
  var pulseTimer = null;
  var mosaicDirty = true;

  // ------------------------------------------------------------ iconos pixel

  var ICONS = {
    larva: ['........', '...##...', '..####..', '.######.', '..####..', '...##...', '........', '........'],
    bubble: ['..####..', '.#....#.', '#..##..#', '#.#..#.#', '#..##..#', '.#....#.', '..####..', '........'],
    moon: ['...####.', '..##....', '.##.....', '.##.....', '.##.....', '..##....', '...####.', '........'],
    drop: ['...##...', '...##...', '..####..', '.######.', '.######.', '.######.', '..####..', '........'],
    hand: ['..#..#..', '.##.##..', '########', '########', '.######.', '.######.', '..####..', '........'],
    palm: ['...##...', '..####..', '.######.', '##.##.##', '...##...', '...##...', '...##...', '........'],
    hat: ['........', '..####..', '.######.', '########', '.######.', '........', '........', '........'],
    grid: ['########', '#......#', '#.####.#', '#.#..#.#', '#.####.#', '#......#', '########', '........'],
    lock: ['..####..', '.#....#.', '.#....#.', '########', '###..###', '###..###', '########', '........'],
    check: ['........', '......##', '.....##.', '##..##..', '.###....', '..#.....', '........', '........'],
    star: ['...##...', '..####..', '.######.', '########', '.######.', '..####..', '...##...', '........']
  };

  function pixelSvg(name) {
    var rows = ICONS[name] || ICONS.star;
    var rects = '';
    for (var y = 0; y < rows.length; y++) {
      for (var x = 0; x < rows[y].length; x++) {
        if (rows[y][x] === '#') {
          rects += '<rect x="' + x + '" y="' + y + '" width="1" height="1"/>';
        }
      }
    }
    return '<svg class="px-icon" viewBox="0 0 8 8" shape-rendering="crispEdges" ' +
      'fill="currentColor" aria-hidden="true" focusable="false">' + rects + '</svg>';
  }

  // ------------------------------------------------------------ helpers

  function $(id) { return document.getElementById(id); }

  function toast(text) {
    var node = $('toast');
    if (!node) return;
    node.textContent = text;
    node.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { node.classList.remove('show'); }, 2000);
  }

  function haptic() {
    try { if (window.Nahua && Nahua.haptic) Nahua.haptic('press'); } catch (error) { /* sin host */ }
  }

  function stageBadge() {
    var scene = soulApi.SCENES.filter(function (item) { return item.id === soul.scene; })[0];
    $('stageBadge').textContent = scene ? scene.name.toUpperCase() : soul.scene.toUpperCase();
  }

  // ------------------------------------------------------------ barras

  var STATS = [
    { key: 'food', label: 'Comida', icon: 'larva', color: 'var(--amber)' },
    { key: 'mood', label: 'Ánimo', icon: 'star', color: 'var(--coral)' },
    { key: 'energy', label: 'Energía', icon: 'drop', color: 'var(--blue)' },
    { key: 'clean', label: 'Limpieza', icon: 'drop', color: 'var(--jade)' }
  ];

  function buildBars() {
    var host = $('bars');
    var html = '';
    STATS.forEach(function (stat) {
      var segs = '';
      for (var i = 0; i < 10; i++) segs += '<i></i>';
      html += '<div class="stat" data-key="' + stat.key + '">' +
        '<div class="stat-head">' +
          '<span class="stat-name">' + pixelSvg(stat.icon) + stat.label + '</span>' +
          '<span class="stat-val" id="val-' + stat.key + '">0%</span>' +
        '</div>' +
        '<div class="segbar" style="--fill:' + stat.color + '">' + segs + '</div>' +
        '</div>';
    });
    host.innerHTML = html;
  }

  function paintBar(key, value) {
    var stat = document.querySelector('.stat[data-key="' + key + '"]');
    if (!stat) return;
    var segs = stat.querySelectorAll('.segbar i');
    var on = Math.round(value / 10);
    for (var i = 0; i < segs.length; i++) {
      segs[i].className = i < on ? 'on' : '';
    }
    $('val-' + key).textContent = Math.round(value) + '%';
  }

  // ------------------------------------------------------------ paneles

  function buildWorld() {
    var host = $('sceneList');
    host.innerHTML = soulApi.SCENES.map(function (scene) {
      return '<button class="opt" data-scene="' + scene.id + '">' +
        '<span class="swatch" data-sw="' + scene.id + '"></span>' +
        '<span class="opt-label">' + scene.name + '</span>' +
        '<span class="opt-mark">' + pixelSvg('check') + '</span>' +
        '</button>';
    }).join('');

    var speciesHost = $('speciesList');
    speciesHost.innerHTML = soulApi.SPECIES.map(function (species) {
      return '<button class="opt" data-species="' + species.id + '">' +
        '<span class="opt-label">' + species.name + '</span>' +
        '<span class="opt-mark">' + pixelSvg('check') + '</span>' +
        '</button>';
    }).join('');

    var accHost = $('accList');
    accHost.innerHTML = soulApi.ACCESSORIES.map(function (item) {
      return '<button class="opt" data-acc="' + item.id + '">' +
        '<span class="opt-label">' + item.name + '</span>' +
        '<span class="opt-mark">' + pixelSvg('check') + '</span>' +
        '</button>';
    }).join('');

    host.addEventListener('click', function (event) {
      var button = event.target.closest('[data-scene]');
      if (!button) return;
      haptic();
      soulApi.act(soul, 'scene', button.getAttribute('data-scene'));
      engine.setScene(soul.scene);
      save();
      render();
      say('El agua de aquí se siente distinta.');
    });

    speciesHost.addEventListener('click', function (event) {
      var button = event.target.closest('[data-species]');
      if (!button) return;
      haptic();
      soul.species = button.getAttribute('data-species');
      engine.setSpecies(soul.species);
      save();
      render();
      toast('Nueva especie adoptada');
    });

    accHost.addEventListener('click', function (event) {
      var button = event.target.closest('[data-acc]');
      if (!button) return;
      var id = button.getAttribute('data-acc');
      if (soul.unlocked.indexOf(id) === -1) {
        toast('Se desbloquea cuidando a tu criatura');
        return;
      }
      haptic();
      soulApi.act(soul, 'equip', id);
      engine.setAccessories(soul.equipped);
      save();
      render();
      say('¿Me queda bien?');
    });
  }

  function paintWorld() {
    document.querySelectorAll('[data-scene]').forEach(function (button) {
      button.classList.toggle('sel', button.getAttribute('data-scene') === soul.scene);
    });
    document.querySelectorAll('[data-species]').forEach(function (button) {
      button.classList.toggle('sel', button.getAttribute('data-species') === soul.species);
    });
    document.querySelectorAll('[data-acc]').forEach(function (button) {
      var id = button.getAttribute('data-acc');
      var unlocked = soul.unlocked.indexOf(id) >= 0;
      var equipped = soul.equipped.indexOf(id) >= 0;
      button.classList.toggle('locked', !unlocked);
      button.classList.toggle('sel', equipped);
      var mark = button.querySelector('.opt-mark');
      mark.innerHTML = unlocked ? pixelSvg('check') : pixelSvg('lock');
    });
  }

  // ------------------------------------------------------------ alma / mosaico

  function paintSoul() {
    var canvas = $('mosaic');
    var encoded = mosaic.encode(soul);
    mosaic.render(canvas, encoded.matrix, {
      caption: soul.name + ' · nv.' + soulApi.snapshot(soul).level
    });
    $('soulCode').value = encoded.code;
    $('genes').innerHTML = [
      { key: 'curiosity', label: 'Curiosidad' },
      { key: 'social', label: 'Sociabilidad' },
      { key: 'vitality', label: 'Vitalidad' },
      { key: 'appetite', label: 'Apetito' }
    ].map(function (gene) {
      var value = soul.genes[gene.key];
      var segs = '';
      for (var i = 0; i < 10; i++) segs += '<i class="' + (i < Math.round(value / 10) ? 'on' : '') + '"></i>';
      return '<div class="gene"><span class="gene-label">' + gene.label + '</span>' +
        '<span class="segbar slim">' + segs + '</span>' +
        '<span class="gene-val">' + value + '</span></div>';
    }).join('');
    mosaicDirty = false;
  }

  // ------------------------------------------------------------ render

  function say(text) {
    $('thoughtText').textContent = text;
  }

  function render() {
    var snap = soulApi.snapshot(soul);

    $('petName').value = snap.name;
    $('levelChip').textContent = snap.stage + ' · Nv. ' + snap.level;
    $('speciesLine').textContent = speciesName(snap.species) + ' · ' + snap.moodLabel +
      (snap.night ? ' · Noche' : '');

    STATS.forEach(function (stat) { paintBar(stat.key, soul[stat.key]); });

    if (snap.maxLevel) {
      $('xpText').textContent = 'MÁXIMO';
      $('xpBar').style.width = '100%';
    } else {
      $('xpText').textContent = snap.xp + ' / ' + snap.xpNeed;
      $('xpBar').style.width = Math.round(snap.xpRatio * 100) + '%';
    }

    $('affection').textContent = snap.affection + '%';
    $('stage').setAttribute('data-scene', soul.scene);
    stageBadge();
    paintWorld();
    if (!document.getElementById('panel-soul').classList.contains('hidden')) paintSoul();
  }

  function speciesName(id) {
    var found = soulApi.SPECIES.filter(function (item) { return item.id === id; })[0];
    return found ? found.name : id;
  }

  function save() { soulApi.save(soul); }

  // ------------------------------------------------------------ acciones

  function act(type) {
    haptic();
    var result = soulApi.act(soul, type);
    soul.memory.behavior = result.behavior;
    soul.memory.behaviorUntil = Date.now() + 6000;
    if (type === 'feed' || type === 'play' || type === 'sleep' || type === 'bath' || type === 'pet') {
      var lines = soulApi.ACTION_THOUGHTS[type];
      soul.memory.thought = lines[Math.floor(Math.random() * lines.length)];
    }
    if (result.leveled) {
      var gift = soulApi.ACCESSORIES[Math.min(5, result.level - 1)];
      toast('¡' + soulApi.STAGES[result.level - 1] + '! Desbloqueaste: ' + gift.name);
    }
    engine.setBehavior(result.behavior);
    save();
    render();
    say(soul.memory.thought);
    if (type === 'sleep') {
      setTimeout(function () { render(); }, 1800);
    }
  }

  // ------------------------------------------------------------ arranque

  function boot() {
    buildBars();
    buildWorld();
    engine = window.YolcatlEngine.create($('gl'), $('fx'));
    engine.setSpecies(soul.species);
    engine.setScene(soul.scene);
    engine.setAccessories(soul.equipped);
    engine.setBehavior(soul.memory.behavior || 'observe');
    engine.resize();
    render();

    if (!soul.memory.thought) {
      soul.memory.thought = soulApi.pulse(soul, new Date()) && soul.memory.thought;
      save();
    }
    say(soul.memory.thought || '…');

    if (loaded.migrated) {
      toast('Alma de la versión anterior recuperada');
    } else if (window.Nahua && Nahua.user && Nahua.user().name) {
      say('Hola ' + Nahua.user().name.split(' ')[0] + '. Soy ' + soul.name + '.');
    }

    document.querySelectorAll('[data-action]').forEach(function (button) {
      button.addEventListener('click', function () {
        act(button.getAttribute('data-action'));
      });
    });

    $('closeBtn').addEventListener('click', function () {
      try {
        soulApi.save(soul);
        if (window.Nahua && Nahua.close) Nahua.close();
        else history.back();
      } catch (error) {
        history.back();
      }
    });

    $('gl').addEventListener('click', function () { act('pet'); });

    $('petName').addEventListener('change', function (event) {
      var value = event.target.value.trim().slice(0, 12);
      soul.name = value || 'Xolito';
      event.target.value = soul.name;
      save();
      toast('Nombre guardado');
    });

    document.querySelectorAll('.tab').forEach(function (tab) {
      tab.addEventListener('click', function () {
        var name = tab.getAttribute('data-tab');
        document.querySelectorAll('.tab').forEach(function (other) {
          other.classList.toggle('active', other === tab);
        });
        ['care', 'world', 'soul'].forEach(function (panel) {
          $('panel-' + panel).classList.toggle('hidden', panel !== name);
        });
        if (name === 'soul') paintSoul();
      });
    });

    $('copySoul').addEventListener('click', function () {
      var code = $('soulCode').value;
      var done = function () { toast('Código del alma copiado'); };
      if (window.Nahua && Nahua.copy) {
        try { Nahua.copy(code); done(); return; } catch (error) { /* sigue abajo */ }
      }
      try {
        $('soulCode').select();
        document.execCommand('copy');
        done();
      } catch (error) {
        toast('Selecciona el código y cópialo a mano');
      }
    });

    $('importSoul').addEventListener('click', function () {
      var code = $('soulCode').value;
      var decoded = mosaic.decode(code);
      if (!decoded) {
        toast('El mosaico no cuadra: CRC inválido');
        return;
      }
      soul = soulApi.sanitize(decoded);
      soul.memory.behavior = 'greetUser';
      soul.memory.thought = 'Volví de otro estanque.';
      soulApi.pulse(soul, new Date());
      save();
      engine.setSpecies(soul.species);
      engine.setScene(soul.scene);
      engine.setAccessories(soul.equipped);
      render();
      say(soul.memory.thought);
      toast('Alma importada');
    });

    $('resetSoul').addEventListener('click', function () {
      if (!window.confirm('¿Empezar con una criatura nueva? El alma actual se pierde.')) return;
      soul = soulApi.reset();
      save();
      engine.setSpecies(soul.species);
      engine.setScene(soul.scene);
      engine.setAccessories(soul.equipped);
      render();
      say('Nací otra vez. Cuídame.');
    });

    // Decaimiento en vivo: cada 60 s, sin umbrales y con floats.
    setInterval(function () {
      soulApi.applyDecay(soul, 60000);
      save();
      render();
    }, 60000);

    schedulePulse();

    document.addEventListener('visibilitychange', function () {
      if (document.hidden) {
        save();
        resumeAt = Date.now();
      } else {
        if (resumeAt) {
          soulApi.applyDecay(soul, Date.now() - resumeAt);
          save();
          render();
        }
        schedulePulse();
      }
    });

    var resize = function () { if (engine) engine.resize(); };
    window.addEventListener('resize', resize);
    window.addEventListener('orientationchange', function () { setTimeout(resize, 220); });
    if (window.ResizeObserver) new ResizeObserver(resize).observe($('stage'));
  }

  var resumeAt = 0;

  function schedulePulse() {
    clearTimeout(pulseTimer);
    if (document.hidden) return;
    var wait = 5000 + Math.random() * 10000;
    pulseTimer = setTimeout(function () {
      soulApi.pulse(soul, new Date());
      engine.setBehavior(soul.memory.behavior);
      save();
      render();
      say(soul.memory.thought);
      schedulePulse();
    }, wait);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
