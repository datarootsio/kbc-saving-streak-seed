/* ============================================================
   Small animation toolkit: number tweens, confetti, ripples,
   flying coins and toasts. No dependencies.
   ============================================================ */
(function (global) {
  'use strict';

  var reduceMotion = global.matchMedia && global.matchMedia('(prefers-reduced-motion: reduce)').matches;

  var euro = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'EUR', minimumFractionDigits: 2 });
  var plain = new Intl.NumberFormat('en-GB');
  var decimals = new Intl.NumberFormat('en-GB',
    { minimumFractionDigits: 2, maximumFractionDigits: 2, useGrouping: false });

  function easeOutCubic(t) {
    return 1 - Math.pow(1 - t, 3);
  }

  /** Tweens a number and writes it into the element on every frame. */
  function countTo(el, to, options) {
    if (!el) return;
    var opts = options || {};
    var format = opts.format || function (value) { return plain.format(Math.round(value)); };
    var from = typeof opts.from === 'number' ? opts.from : Number(el.dataset.value || 0);
    var duration = reduceMotion ? 0 : (opts.duration || 900);

    if (el._tween) {
      cancelAnimationFrame(el._tween);
    }
    el.dataset.value = String(to);

    if (duration === 0 || from === to) {
      el.textContent = format(to);
      return;
    }

    var start = performance.now();
    function frame(now) {
      var t = Math.min(1, (now - start) / duration);
      var value = from + (to - from) * easeOutCubic(t);
      el.textContent = format(value);
      if (t < 1) {
        el._tween = requestAnimationFrame(frame);
      } else {
        el.textContent = format(to);
        el._tween = null;
      }
    }
    el._tween = requestAnimationFrame(frame);
  }

  function countMoney(el, to, options) {
    var opts = options || {};
    opts.format = function (value) { return euro.format(value); };
    countTo(el, to, opts);
  }

  /* ── Confetti ─────────────────────────────────────────── */

  var canvas, ctx, pieces = [], running = false;
  var palette = ['#4a9bd6', '#10375c', '#ffd977', '#e9a72f', '#2e9c6b', '#ffffff'];

  function ensureCanvas() {
    if (canvas) return;
    canvas = document.getElementById('confetti');
    ctx = canvas.getContext('2d');
    resize();
    global.addEventListener('resize', resize);
  }

  function resize() {
    var ratio = global.devicePixelRatio || 1;
    canvas.width = global.innerWidth * ratio;
    canvas.height = global.innerHeight * ratio;
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
  }

  function confetti(originX, originY, amount) {
    if (reduceMotion) return;
    ensureCanvas();
    canvas.classList.add('is-on');
    var x = typeof originX === 'number' ? originX : global.innerWidth / 2;
    var y = typeof originY === 'number' ? originY : global.innerHeight / 3;
    var count = amount || 90;

    for (var i = 0; i < count; i++) {
      var angle = (-Math.PI / 2) + (Math.random() - 0.5) * 2.1;
      var speed = 5 + Math.random() * 9;
      pieces.push({
        x: x, y: y,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed,
        size: 5 + Math.random() * 6,
        rotation: Math.random() * Math.PI,
        spin: (Math.random() - 0.5) * 0.32,
        color: palette[(Math.random() * palette.length) | 0],
        life: 1
      });
    }
    if (!running) {
      running = true;
      requestAnimationFrame(tick);
    }
  }

  function tick() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    for (var i = pieces.length - 1; i >= 0; i--) {
      var p = pieces[i];
      p.vy += 0.28;
      p.vx *= 0.995;
      p.x += p.vx;
      p.y += p.vy;
      p.rotation += p.spin;
      p.life -= 0.008;
      if (p.life <= 0 || p.y > global.innerHeight + 40) {
        pieces.splice(i, 1);
        continue;
      }
      ctx.save();
      ctx.globalAlpha = Math.max(0, Math.min(1, p.life));
      ctx.translate(p.x, p.y);
      ctx.rotate(p.rotation);
      ctx.fillStyle = p.color;
      ctx.fillRect(-p.size / 2, -p.size / 2, p.size, p.size * 0.62);
      ctx.restore();
    }
    if (pieces.length) {
      requestAnimationFrame(tick);
    } else {
      running = false;
      canvas.classList.remove('is-on');
    }
  }

  /* ── Ripple on click ─────────────────────────────────── */

  function ripple(event) {
    var target = event.currentTarget;
    if (reduceMotion || !target) return;
    var rect = target.getBoundingClientRect();
    var size = Math.max(rect.width, rect.height);
    var dot = document.createElement('span');
    dot.className = 'ripple';
    dot.style.width = dot.style.height = size + 'px';
    dot.style.left = (event.clientX - rect.left - size / 2) + 'px';
    dot.style.top = (event.clientY - rect.top - size / 2) + 'px';
    target.appendChild(dot);
    setTimeout(function () { dot.remove(); }, 650);
  }

  /* ── Coins flying from one element to another ────────── */

  function flyCoins(fromEl, toEl, count, onArrive) {
    if (!fromEl || !toEl) { if (onArrive) onArrive(); return; }
    if (reduceMotion) { if (onArrive) onArrive(); return; }

    var layer = document.getElementById('coinfly');
    var a = fromEl.getBoundingClientRect();
    var b = toEl.getBoundingClientRect();
    var total = count || 7;

    for (var i = 0; i < total; i++) {
      (function (index) {
        var coin = document.createElement('span');
        coin.className = 'coinfly__coin';
        coin.textContent = '€';
        var startX = a.left + a.width / 2 + (Math.random() - 0.5) * a.width * 0.5;
        var startY = a.top + a.height / 2 + (Math.random() - 0.5) * 20;
        var endX = b.left + b.width / 2 + (Math.random() - 0.5) * b.width * 0.5;
        var endY = b.top + b.height / 2 + (Math.random() - 0.5) * 20;
        coin.style.left = startX + 'px';
        coin.style.top = startY + 'px';
        layer.appendChild(coin);

        var lift = 90 + Math.random() * 70;
        var animation = coin.animate([
          { transform: 'translate(0,0) scale(.5)', opacity: 0 },
          { transform: 'translate(' + (endX - startX) / 2 + 'px,' + ((endY - startY) / 2 - lift) + 'px) scale(1.1)', opacity: 1, offset: 0.5 },
          { transform: 'translate(' + (endX - startX) + 'px,' + (endY - startY) + 'px) scale(.4)', opacity: 0 }
        ], {
          duration: 750 + index * 60,
          delay: index * 55,
          easing: 'cubic-bezier(.35,.05,.3,1)',
          fill: 'forwards'
        });
        animation.onfinish = function () {
          coin.remove();
          if (index === total - 1 && onArrive) onArrive();
        };
      })(i);
    }
  }

  /* ── Toasts ──────────────────────────────────────────── */

  function toast(title, message, kind) {
    var host = document.getElementById('toasts');
    var el = document.createElement('div');
    el.className = 'toast' + (kind ? ' toast--' + kind : '');
    var strong = document.createElement('strong');
    strong.textContent = title;
    el.appendChild(strong);
    if (message) {
      var span = document.createElement('span');
      span.textContent = message;
      el.appendChild(span);
    }
    host.appendChild(el);
    setTimeout(function () {
      el.classList.add('is-out');
      setTimeout(function () { el.remove(); }, 400);
    }, 4200);
  }

  /** Retriggers a CSS animation class on an element. */
  function pulse(el, className, duration) {
    if (!el) return;
    el.classList.remove(className);
    void el.offsetWidth;
    el.classList.add(className);
    setTimeout(function () { el.classList.remove(className); }, duration || 900);
  }

  global.FX = {
    countTo: countTo,
    countMoney: countMoney,
    confetti: confetti,
    ripple: ripple,
    flyCoins: flyCoins,
    toast: toast,
    pulse: pulse,
    formatEuro: function (value) { return euro.format(value); },
    formatNumber: function (value) { return plain.format(value); },
    formatDecimal: function (value) { return decimals.format(value); },
    reduceMotion: reduceMotion
  };
})(window);
