/*
 * YOLCATL · ai-soul.js
 * Genética, memoria emocional y Utility AI 100% offline.
 *
 * Reglas que este archivo NO rompe:
 *  - Las barras se guardan como flotantes; el redondeo ocurre sólo al pintar.
 *  - El decaimiento corre en cada tick (60 s) sin umbrales: la mascota envejece en vivo.
 *  - Ninguna petición de red. Todo vive en localStorage.
 */
(function (global) {
  'use strict';

  var KEY = 'nahua.yolcatl.v2';
  var LEGACY_KEY = 'nahua.axolote.v1';
  var HOUR = 3600000;
  var MAX_CATCHUP_HOURS = 24;

  var STAGES = ['Cría', 'Explorador', 'Juvenil', 'Guardián', 'Espíritu del lago'];
  // XP acumulada necesaria para alcanzar cada nivel (índice 0 = nivel 1).
  var XP_LADDER = [0, 40, 95, 165, 250];
  var MAX_LEVEL = 5;

  var DECAY_PER_HOUR = { food: 9, mood: 7, energy: 5, clean: 4 };

  var SPECIES = [
    { id: 'axolote', name: 'Axolote de Xochimilco' },
    { id: 'dorado', name: 'Ajolote dorado albino' },
    { id: 'tortuga', name: 'Tortuga de río' },
    { id: 'xolo', name: 'Xoloitzcuintle cachorro' },
    { id: 'colibri', name: 'Colibrí' }
  ];

  var SCENES = [
    { id: 'chinampa', name: 'Chinampa de Xochimilco' },
    { id: 'cenote', name: 'Cenote nocturno' },
    { id: 'playa', name: 'Estero de Colima' },
    { id: 'cuarto', name: 'Cuarto acogedor' }
  ];

  var ACCESSORIES = [
    { id: 'sombrero', name: 'Sombrero de palma', socket: 'head' },
    { id: 'corona', name: 'Corona de flores', socket: 'head' },
    { id: 'gafas', name: 'Gafas pixeladas', socket: 'head' },
    { id: 'bufanda', name: 'Bufanda de lana', socket: 'neck' },
    { id: 'mochila', name: 'Mochila de camino', socket: 'back' },
    { id: 'pelota', name: 'Pelota flotante', socket: 'floor' }
  ];

  var THOUGHTS = {
    sleep: ['Zzz… el agua está tibia.', 'Cinco minutos más de sueño.', 'Sueño con canales turquesa.'],
    seekFood: ['¿Dónde quedaron las larvas?', 'Huelo alimento río abajo.', 'Mi panza hace burbujas.'],
    play: ['¡Burbujas! Otra vez.', 'Persigo la luz del agua.', 'Esto es mejor que dormir.'],
    greetUser: ['¡Llegaste!', 'Te estaba esperando.', '¡Qué gusto verte otra vez!'],
    observe: ['…', 'Te miro desde el fondo.', 'Hoy el agua está quieta.'],
    cleanUp: ['Me rasco la espalda.', 'Un remolino y listo.', 'Agua limpia, cuerpo feliz.'],
    pose: ['Así me veo bien, ¿no?', 'Mira mis branquias.']
  };

  var ACTION_THOUGHTS = {
    feed: ['¡Ñam! Larvas del lago.', 'Gracias, ya tenía hambre.'],
    play: ['¡Burbujas! Otra vez, otra vez.', 'Me encanta jugar contigo.'],
    sleep: ['Zzz… cinco minutos más.', 'Soñé con canales de agua turquesa.'],
    bath: ['¡Agua fresca! Ahora brillo.', 'Sí que hacía falta.'],
    pet: ['Tu mano me da calma.', '¡Qué cosquillas!'],
    scene: ['Aquí se siente distinto el agua.', 'Nuevo rincón, nuevas burbujas.'],
    accessory: ['¿Me queda bien?', 'Me siento elegante.']
  };

  function rand(min, max) { return min + Math.random() * (max - min); }
  function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }

  function freshGenes() {
    return {
      curiosity: Math.round(rand(18, 96)),
      social: Math.round(rand(18, 96)),
      vitality: Math.round(rand(18, 96)),
      appetite: Math.round(rand(18, 96))
    };
  }

  function defaultSoul(name) {
    return {
      schema: 2,
      name: name || 'Xolito',
      species: 'axolote',
      genes: freshGenes(),
      // Barras en flotante (0-100). Se redondean sólo al renderizar.
      food: 80, mood: 80, energy: 80, clean: 80,
      xp: 0,
      scene: 'chinampa',
      equipped: [],
      unlocked: [],
      memory: {
        affection: 40,
        favoriteScene: 'chinampa',
        favoriteAccessory: '',
        sceneTally: { chinampa: 0, cenote: 0, playa: 0, cuarto: 0 },
        accessoryTally: {},
        touches: 0,
        plays: 0,
        feeds: 0,
        baths: 0,
        behavior: 'observe',
        behaviorUntil: 0,
        thought: ''
      },
      born: Date.now(),
      lastSeen: Date.now()
    };
  }

  function levelFor(xp) {
    var level = 1;
    for (var i = 0; i < XP_LADDER.length; i++) {
      if (xp >= XP_LADDER[i]) level = i + 1;
    }
    return Math.min(MAX_LEVEL, level);
  }

  function xpWindow(xp) {
    var level = levelFor(xp);
    if (level >= MAX_LEVEL) return { level: level, into: 0, need: 0, max: true, ratio: 1 };
    var from = XP_LADDER[level - 1];
    var to = XP_LADDER[level];
    return {
      level: level,
      into: Math.round(xp - from),
      need: to - from,
      max: false,
      ratio: clamp((xp - from) / (to - from), 0, 1)
    };
  }

  // ---------------------------------------------------------------- decaimiento

  function decayRate(soul, key) {
    var g = soul.genes || {};
    if (key === 'food') return DECAY_PER_HOUR.food * (1 + (g.appetite || 50) / 200);
    if (key === 'mood') return DECAY_PER_HOUR.mood * (1 + (100 - (g.social || 50)) / 400);
    if (key === 'energy') return DECAY_PER_HOUR.energy * (1 + (g.vitality || 50) / 200);
    return DECAY_PER_HOUR[key];
  }

  /**
   * Aplica el paso del tiempo SIN umbral mínimo: cada tick de 60 s mueve la simulación.
   * El catch-up de una ausencia larga se topa en MAX_CATCHUP_HOURS: volver a la semana no debe
   * encontrar a la criatura muerta de hambre.
   * Devuelve ms efectivos aplicados.
   */
  function applyDecay(soul, ms) {
    var elapsed = Math.max(0, ms || 0);
    var hours = Math.min(elapsed, MAX_CATCHUP_HOURS * HOUR) / HOUR;
    if (hours <= 0) return 0;
    Object.keys(DECAY_PER_HOUR).forEach(function (key) {
      var rate = decayRate(soul, key);
      // Con la app cerrada la mascota duerme: la energía baja a la mitad y no llega a cero.
      if (key === 'energy') rate *= 0.55;
      soul[key] = clamp((soul[key] == null ? 50 : soul[key]) - rate * hours, 0, 100);
    });
    // El afecto también se enfría con la ausencia, pero nunca por debajo de 5.
    if (hours > 1) {
      soul.memory.affection = clamp(soul.memory.affection - hours * 1.6, 0, 100);
    }
    soul.lastSeen = Date.now();
    return elapsed;
  }

  // ---------------------------------------------------------------- utilidad

  function isNight(date) {
    var hour = (date || new Date()).getHours();
    return hour >= 20 || hour < 6;
  }

  /** Puntajes de utilidad. La IA elige el más alto cada pulso (5-15 s). */
  function evaluate(soul, date) {
    var g = soul.genes;
    var night = isNight(date);
    var affection = soul.memory.affection;
    var scores = {
      sleep: (100 - soul.energy) * (1 + g.vitality / 200),
      seekFood: (100 - soul.food) * (1 + g.appetite / 150),
      play: (soul.mood < 60 ? 50 : 20) * (1 + g.curiosity / 100),
      greetUser: affection > 70 ? 45 : 10,
      cleanUp: (100 - soul.clean) * 0.9,
      observe: g.curiosity * 0.55,
      pose: (soul.mood > 75 ? 26 : 8) * (1 + g.social / 200)
    };
    if (night) {
      scores.sleep *= 1.35;
      scores.observe *= 0.7;
      scores.pose *= 0.5;
    }
    // La escena favorita invita a observar/posar: memoria, no azar.
    if (soul.scene === soul.memory.favoriteScene) {
      scores.observe *= 1.25;
      scores.pose *= 1.15;
    }
    if (soul.memory.behavior === 'sleep' && soul.energy < 55) scores.sleep *= 1.4;
    return scores;
  }

  function pick(scores) {
    var best = null;
    var bestScore = -1;
    Object.keys(scores).forEach(function (key) {
      var value = scores[key] * (0.85 + Math.random() * 0.3); // ruido: evita bucles rígidos
      if (value > bestScore) { bestScore = value; best = key; }
    });
    return best;
  }

  function thoughtFor(behavior) {
    var list = THOUGHTS[behavior] || THOUGHTS.observe;
    return list[Math.floor(Math.random() * list.length)];
  }

  /** Pulso autónomo: elige conducta, la sostiene entre 5 y 15 s y actualiza memoria. */
  function pulse(soul, date) {
    var behavior = pick(evaluate(soul, date));
    var now = Date.now();
    soul.memory.behavior = behavior;
    soul.memory.behaviorUntil = now + Math.round(rand(5000, 15000));
    soul.memory.thought = thoughtFor(behavior);

    // Consecuencias mínimas del comportamiento para que no sea sólo pantomima.
    if (behavior === 'sleep') soul.energy = clamp(soul.energy + 2.5, 0, 100);
    if (behavior === 'seekFood' && soul.food < 35) soul.food = clamp(soul.food + 4, 0, 100);
    if (behavior === 'cleanUp') soul.clean = clamp(soul.clean + 3, 0, 100);
    if (behavior === 'greetUser') soul.memory.affection = clamp(soul.memory.affection + 0.6, 0, 100);
    return behavior;
  }

  // ---------------------------------------------------------------- acciones

  function gain(soul, key, amount) { soul[key] = clamp(soul[key] + amount, 0, 100); }

  function act(soul, type, extra) {
    var g = soul.genes;
    var before = levelFor(soul.xp);
    var xpGain = 6;

    if (type === 'feed') {
      gain(soul, 'food', 24 * (1 + g.appetite / 300));
      gain(soul, 'clean', -4);
      soul.memory.feeds += 1;
      soul.memory.affection = clamp(soul.memory.affection + 2, 0, 100);
    } else if (type === 'play') {
      gain(soul, 'mood', 20 * (1 + g.curiosity / 400));
      gain(soul, 'energy', -10);
      gain(soul, 'food', -5);
      soul.memory.plays += 1;
      soul.memory.affection = clamp(soul.memory.affection + 2.5, 0, 100);
      xpGain += Math.round(g.curiosity / 25);
    } else if (type === 'sleep') {
      gain(soul, 'energy', 30 * (1 + g.vitality / 400));
      gain(soul, 'mood', 4);
    } else if (type === 'bath') {
      gain(soul, 'clean', 34);
      gain(soul, 'mood', 6);
      soul.memory.baths += 1;
    } else if (type === 'pet') {
      gain(soul, 'mood', 5 * (1 + g.social / 400));
      soul.memory.touches += 1;
      soul.memory.affection = clamp(soul.memory.affection + 3, 0, 100);
      xpGain = 2;
    } else if (type === 'scene') {
      soul.scene = extra;
      soul.memory.sceneTally[extra] = (soul.memory.sceneTally[extra] || 0) + 1;
      recomputeFavoriteScene(soul);
      xpGain = 1;
    } else if (type === 'equip') {
      toggleAccessory(soul, extra);
      xpGain = 1;
    }

    soul.xp += xpGain;
    var after = levelFor(soul.xp);
    if (after > before) {
      soul.xp += 10; // bonus de etapa
      unlockForLevel(soul, after);
    }
    syncUnlocks(soul);
    return { behavior: type, thought: thoughtFor(type), leveled: after > before, level: after };
  }

  function recomputeFavoriteScene(soul) {
    var best = soul.scene;
    var bestCount = -1;
    Object.keys(soul.memory.sceneTally).forEach(function (key) {
      var count = soul.memory.sceneTally[key] || 0;
      if (count > bestCount) { bestCount = count; best = key; }
    });
    soul.memory.favoriteScene = best;
  }

  function toggleAccessory(soul, id) {
    if (soul.unlocked.indexOf(id) === -1) soul.unlocked.push(id);
    var at = soul.equipped.indexOf(id);
    if (at >= 0) soul.equipped.splice(at, 1);
    else soul.equipped.push(id);
    var tally = soul.memory.accessoryTally;
    tally[id] = (tally[id] || 0) + 1;
    var best = soul.memory.favoriteAccessory;
    var bestCount = best ? (tally[best] || 0) : -1;
    if ((tally[id] || 0) > bestCount) soul.memory.favoriteAccessory = id;
  }

  function unlockForLevel(soul, level) {
    // Cada etapa regala un accesorio nuevo: la personalización crece con el cuidado.
    var gift = ACCESSORIES[Math.min(ACCESSORIES.length - 1, level - 1)];
    if (gift && soul.unlocked.indexOf(gift.id) === -1) {
      soul.unlocked.push(gift.id);
      return gift;
    }
    return null;
  }

  /**
   * Reconstruye los accesorios que un alma ya ganó. Se aplica al cargar y al importar:
   * un mosaico restaurado no puede perder lo que su nivel y su afecto ya merecían.
   */
  function syncUnlocks(soul) {
    var level = levelFor(soul.xp);
    var fresh = [];
    for (var i = 0; i < level && i < ACCESSORIES.length; i++) {
      if (soul.unlocked.indexOf(ACCESSORIES[i].id) < 0) fresh.push(ACCESSORIES[i].id);
    }
    // La pelota flotante se gana con el vínculo, no con la XP.
    if (soul.memory.affection >= 70 && soul.unlocked.indexOf('pelota') < 0) fresh.push('pelota');
    if (fresh.length) soul.unlocked = soul.unlocked.concat(fresh);
    return fresh;
  }

  function moodLabel(soul) {
    var mood = soul.mood;
    if (soul.energy < 18) return 'Dormido';
    if (mood > 85) return 'Radiante';
    if (mood > 65) return 'Contento';
    if (mood > 40) return 'Tranquilo';
    if (mood > 20) return 'Triste';
    return 'Marchito';
  }

  function snapshot(soul) {
    var win = xpWindow(soul.xp);
    return {
      name: soul.name,
      species: soul.species,
      stage: STAGES[win.level - 1],
      level: win.level,
      maxLevel: win.max,
      xp: win.into,
      xpNeed: win.need,
      xpRatio: win.ratio,
      food: Math.round(soul.food),
      mood: Math.round(soul.mood),
      energy: Math.round(soul.energy),
      clean: Math.round(soul.clean),
      moodLabel: moodLabel(soul),
      scene: soul.scene,
      equipped: soul.equipped.slice(),
      unlocked: soul.unlocked.slice(),
      behavior: soul.memory.behavior,
      thought: soul.memory.thought,
      affection: Math.round(soul.memory.affection),
      genes: soul.genes,
      night: isNight()
    };
  }

  // ---------------------------------------------------------------- persistencia

  function sanitize(raw) {
    var base = defaultSoul();
    if (!raw || typeof raw !== 'object') return base;
    var soul = base;
    if (typeof raw.name === 'string' && raw.name.trim()) soul.name = raw.name.trim().slice(0, 12);
    if (SPECIES.some(function (s) { return s.id === raw.species; })) soul.species = raw.species;
    if (raw.genes) {
      ['curiosity', 'social', 'vitality', 'appetite'].forEach(function (key) {
        var value = Number(raw.genes[key]);
        if (isFinite(value)) soul.genes[key] = clamp(Math.round(value), 0, 100);
      });
    }
    ['food', 'mood', 'energy', 'clean'].forEach(function (key) {
      var value = Number(raw[key]);
      if (isFinite(value)) soul[key] = clamp(value, 0, 100);
    });
    var xp = Number(raw.xp);
    if (isFinite(xp)) soul.xp = clamp(xp, 0, 999);
    if (SCENES.some(function (s) { return s.id === raw.scene; })) soul.scene = raw.scene;
    if (Array.isArray(raw.equipped)) soul.equipped = raw.equipped.filter(validAccessory);
    if (Array.isArray(raw.unlocked)) soul.unlocked = raw.unlocked.filter(validAccessory);
    if (raw.memory && typeof raw.memory === 'object') {
      var m = raw.memory;
      var affection = Number(m.affection);
      if (isFinite(affection)) soul.memory.affection = clamp(affection, 0, 100);
      if (SCENES.some(function (s) { return s.id === m.favoriteScene; })) soul.memory.favoriteScene = m.favoriteScene;
      if (typeof m.favoriteAccessory === 'string') soul.memory.favoriteAccessory = m.favoriteAccessory;
      if (m.sceneTally && typeof m.sceneTally === 'object') {
        Object.keys(soul.memory.sceneTally).forEach(function (key) {
          var count = Number(m.sceneTally[key]);
          if (isFinite(count)) soul.memory.sceneTally[key] = clamp(count, 0, 9999);
        });
      }
      ['touches', 'plays', 'feeds', 'baths'].forEach(function (key) {
        var count = Number(m[key]);
        if (isFinite(count)) soul.memory[key] = clamp(Math.round(count), 0, 9999);
      });
      soul.memory.behavior = 'observe';
      soul.memory.behaviorUntil = 0;
      soul.memory.thought = '';
    }
    if (isFinite(Number(raw.born))) soul.born = Number(raw.born);
    if (isFinite(Number(raw.lastSeen))) soul.lastSeen = Number(raw.lastSeen);
    syncUnlocks(soul);
    return soul;
  }

  function validAccessory(id) {
    return ACCESSORIES.some(function (item) { return item.id === id; });
  }

  function readStorage(key) {
    try {
      var raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) : null;
    } catch (error) {
      return null;
    }
  }

  function load() {
    var current = readStorage(KEY);
    if (current) {
      var soul = sanitize(current);
      applyDecay(soul, Date.now() - (soul.lastSeen || Date.now()));
      return { soul: soul, migrated: false };
    }
    var legacy = readStorage(LEGACY_KEY);
    if (legacy) {
      // El alma de la v1 se conserva: mismo nombre, mismas barras, misma XP.
      var migrated = sanitize({
        name: legacy.name,
        food: legacy.food,
        mood: legacy.mood,
        energy: legacy.energy,
        clean: legacy.clean,
        xp: legacy.xp,
        lastSeen: legacy.lastSeen
      });
      applyDecay(migrated, Date.now() - (migrated.lastSeen || Date.now()));
      return { soul: migrated, migrated: true };
    }
    return { soul: defaultSoul('Xolito'), migrated: false };
  }

  function save(soul) {
    soul.lastSeen = Date.now();
    try { localStorage.setItem(KEY, JSON.stringify(soul)); } catch (error) { /* almacenamiento lleno */ }
  }

  function reset() {
    try { localStorage.removeItem(KEY); } catch (error) { /* nada */ }
    return defaultSoul('Xolito');
  }

  global.YolcatlSoul = {
    KEY: KEY,
    STAGES: STAGES,
    SPECIES: SPECIES,
    SCENES: SCENES,
    ACCESSORIES: ACCESSORIES,
    ACTION_THOUGHTS: ACTION_THOUGHTS,
    MAX_LEVEL: MAX_LEVEL,
    defaultSoul: defaultSoul,
    sanitize: sanitize,
    levelFor: levelFor,
    xpWindow: xpWindow,
    applyDecay: applyDecay,
    evaluate: evaluate,
    pulse: pulse,
    act: act,
    snapshot: snapshot,
    isNight: isNight,
    load: load,
    save: save,
    reset: reset
  };
})(window);
