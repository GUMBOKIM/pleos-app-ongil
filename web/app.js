// 온길 공유 페이지. #m=<base64url json> (내 지도 히트맵) / #c=<base64url json> (여행 기록·스탬프 카드). 서버 없음.
(function () {
  var PAINT = '#0E9A8A', SOFT = '#B9E3DD', NONE = '#E4F0EE', LINE = '#C9DBD8', BG = '#F3F9F8', INK = '#10201E', MUTED = '#5C6A68';
  var $ = function (id) { return document.getElementById(id); };

  function decode(b64) {
    var s = b64.replace(/-/g, '+').replace(/_/g, '/'); while (s.length % 4) s += '=';
    var bin = atob(s), bytes = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return JSON.parse(new TextDecoder('utf-8').decode(bytes));
  }
  function parseHash() {
    var h = location.hash.replace(/^#/, '');
    var m = /^(m|c|p)=(.+)$/.exec(h); if (!m) return null;
    try { return { kind: m[1], data: decode(m[2]) }; } catch (e) { return null; }
  }
  function lerp(a, b, t) { return a + (b - a) * t; }
  function hex(c) { return [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)]; }
  function mix(c1, c2, t) { var a = hex(c1), b = hex(c2); return 'rgb(' + Math.round(lerp(a[0], b[0], t)) + ',' + Math.round(lerp(a[1], b[1], t)) + ',' + Math.round(lerp(a[2], b[2], t)) + ')'; }
  function heatColor(level) { return level <= 0 ? NONE : mix(SOFT, PAINT, Math.min(1, Math.max(0, (level - 0.3) / 0.7))); }

  // 앱과 같은 투영: 위도 36.3 기준 등장방형. 전국 bbox에 맞춰 캔버스에 놓는다
  function project(lat, lng) { return [lng * Math.cos(36.3 * Math.PI / 180), -lat]; }
  function drawKorea(ctx, regions, box, heat, done, highlight) {
    var minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
    // 전국 또는 (카드) 그 지역 주변만: 지역 bbox를 2.6배로 넓혀 이웃까지 보이게
    var scope = highlight ? regions.filter(function (r) { return r.code === highlight; }) : regions;
    scope.forEach(function (r) { r.rings.forEach(function (ring) { ring.forEach(function (p) { var q = project(p[1], p[0]); if (q[0] < minX) minX = q[0]; if (q[0] > maxX) maxX = q[0]; if (q[1] < minY) minY = q[1]; if (q[1] > maxY) maxY = q[1]; }); }); });
    if (highlight) { var cx = (minX + maxX) / 2, cy = (minY + maxY) / 2, hw = Math.max(maxX - minX, (maxY - minY) * box.w / box.h) * 1.3; minX = cx - hw; maxX = cx + hw; minY = cy - hw * box.h / box.w; maxY = cy + hw * box.h / box.w; }
    var s = Math.min(box.w / (maxX - minX), box.h / (maxY - minY)) * (highlight ? 1 : 0.94);
    var ox = box.x + (box.w - (maxX - minX) * s) / 2 - minX * s, oy = box.y + (box.h - (maxY - minY) * s) / 2 - minY * s;
    ctx.save(); ctx.beginPath(); ctx.rect(box.x, box.y, box.w, box.h); ctx.clip();
    function path(r) { r.rings.forEach(function (ring) { ring.forEach(function (p, i) { var q = project(p[1], p[0]); var x = q[0] * s + ox, y = q[1] * s + oy; if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y); }); ctx.closePath(); }); }
    regions.forEach(function (r) {
      ctx.beginPath(); path(r);
      ctx.fillStyle = highlight ? (r.code === highlight ? PAINT : NONE) : heatColor(heat[r.code] || 0); ctx.fill();
      ctx.lineWidth = highlight ? 2 : 1; ctx.strokeStyle = LINE; ctx.stroke();
    });
    (done || []).forEach(function (code) { var r = regions.filter(function (x) { return x.code === code; })[0]; if (!r) return; ctx.beginPath(); path(r); ctx.lineWidth = 3; ctx.strokeStyle = PAINT; ctx.stroke(); });
    ctx.restore();
  }
  function text(ctx, t, x, y, size, color, weight, align) { ctx.font = (weight || 700) + ' ' + size + 'px -apple-system, "Apple SD Gothic Neo", "Noto Sans KR", sans-serif'; ctx.fillStyle = color; ctx.textAlign = align || 'left'; ctx.fillText(t, x, y); }

  function renderMap(ctx, regions, d) {
    var heat = {}; (d.h || '').split(',').forEach(function (kv) { var p = kv.split(':'); if (p[0]) heat[p[0]] = parseFloat(p[1]) || 0; });
    var done = (d.d || '').split(',').filter(Boolean);
    ctx.fillStyle = BG; ctx.fillRect(0, 0, 1080, 1350);
    text(ctx, '온길', 60, 110, 56, INK, 900);
    text(ctx, '차로 채운 내 대한민국 지도', 60, 160, 30, MUTED, 500);
    drawKorea(ctx, regions, { x: 60, y: 200, w: 960, h: 820 }, heat, done, null);
    text(ctx, String(d.n || 0), 60, 1180, 112, PAINT, 900); text(ctx, '지역', 60, 1230, 30, MUTED, 500);
    text(ctx, String(d.c || 0), 460, 1180, 112, INK, 900); text(ctx, '스탬프', 460, 1230, 30, MUTED, 500);
    text(ctx, String(d.s || 0), 760, 1180, 112, INK, 900); text(ctx, '방문', 760, 1230, 30, MUTED, 500);
    text(ctx, '온길 · 차가 선 명소가 많을수록 진하게 · 스탬프 받은 땅은 테두리', 60, 1300, 24, MUTED, 400);
    $('title').textContent = '내 대한민국 지도'; $('sub').textContent = (d.n || 0) + '지역 · 스탬프 ' + (d.c || 0) + ' · 방문 ' + (d.s || 0);
    $('list').hidden = true;
  }
  function renderCard(ctx, regions, d) {
    var complete = d.t === 'complete', spots = (d.s || '').split('|').filter(Boolean), ach = (d.a || '').split('|').filter(Boolean);
    var region = regions.filter(function (r) { return r.name === d.r; })[0];
    ctx.fillStyle = BG; ctx.fillRect(0, 0, 1080, 1350);
    text(ctx, d.d || '', 60, 100, 30, MUTED, 500);
    text(ctx, complete ? ((d.set || d.r || '') + ' 스탬프') : ((d.r || '') + ((d.day || 1) > 1 ? ' · 이어서' : '')), 60, 170, 64, INK, 900);
    text(ctx, complete ? (d.n || '') + ' 모두 가서 받았어요' : '오늘 ' + spots.length + '곳 · ' + (d.r || '') + ' ' + (d.n || ''), 60, 220, 32, PAINT, 700);
    drawKorea(ctx, regions, { x: 60, y: 260, w: 960, h: 620 }, {}, complete && region ? [region.code] : [], region ? region.code : null);
    var y = 960; text(ctx, complete ? '세트 명소' : '오늘 방문', 60, y, 26, MUTED, 500); y += 44;
    spots.slice(0, 8).forEach(function (n) { ctx.beginPath(); ctx.arc(74, y - 10, 9, 0, Math.PI * 2); ctx.fillStyle = PAINT; ctx.fill(); text(ctx, n, 100, y, 32, INK, 600); y += 46; });
    ach.forEach(function (a) { text(ctx, (/스탬프$/.test(a) ? '스탬프 · ' : '도전과제 · ') + a, 60, y, 28, PAINT, 700); y += 40; });
    var foot = ['온길', d.km ? d.km + 'km' : null, '차 위치·정차로 기록'].filter(Boolean).join(' · ');
    text(ctx, foot, 60, 1300, 24, MUTED, 400);
    $('title').textContent = complete ? ((d.set || d.r || '') + ' 스탬프 카드') : ((d.r || '') + ' 여행 기록'); $('sub').textContent = (d.d || '') + ' · ' + spots.length + '곳';
    $('list').hidden = true;   // 명소는 카드 그림 안에 이미 있다
  }

  function main() {
    var p = parseHash(); if (!p) return;
    fetch('sgg-simple.json').then(function (r) { return r.json(); }).then(function (geo) {
      var c = $('c'), ctx = c.getContext('2d');
      if (p.kind === 'm') renderMap(ctx, geo.regions, p.data); else renderCard(ctx, geo.regions, p.data);
      c.hidden = false; $('empty').hidden = true; $('actions').hidden = false;
      $('save').onclick = function () { var a = document.createElement('a'); a.download = 'ongil-' + p.kind + '.png'; a.href = c.toDataURL('image/png'); a.click(); };
      $('share').onclick = function () {
        c.toBlob(function (blob) {
          var f = new File([blob], 'ongil.png', { type: 'image/png' });
          if (navigator.canShare && navigator.canShare({ files: [f] })) navigator.share({ files: [f], title: $('title').textContent }).catch(function () {});
          else $('save').click();
        });
      };
    }).catch(function (e) { $('sub').textContent = '지도를 못 불러왔어요: ' + e; });
  }
  main(); window.addEventListener('hashchange', main);
})();
