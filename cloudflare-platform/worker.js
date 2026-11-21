/**
 * Elysium Training — Separate Cloudflare Worker Web Platform
 * Reads directly from your Cloudflare D1 database binding (env.DB)
 * Displays:
 *  - Every person's profile (Height, Weight, BMI, Body Fat %, Sex, Age)
 *  - Exercise progress, PRs, and full logged set weights (kg) & reps
 *  - Offline browser caching via localStorage when disconnected
 */
export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === "/api/dashboard") {
      try {
        await env.DB.prepare(
          "CREATE TABLE IF NOT EXISTS workout_sync (id INTEGER PRIMARY KEY AUTOINCREMENT, data TEXT, timestamp DATETIME DEFAULT CURRENT_TIMESTAMP);"
        ).run();
        await env.DB.prepare(
          "CREATE TABLE IF NOT EXISTS exercise_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, log_key TEXT UNIQUE, user_id INTEGER, username TEXT, program_epoch_days INTEGER, category_id INTEGER, exercise_name TEXT, target_sets INTEGER, target_reps INTEGER, set_number INTEGER, completed_sets INTEGER, completed_reps INTEGER, weight_kg REAL, is_super_set INTEGER, order_index INTEGER, logged_at INTEGER, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP);"
        ).run();

        const { results } = await env.DB.prepare(
          "SELECT id, data, timestamp FROM workout_sync ORDER BY id DESC LIMIT 100;"
        ).all();
        const { results: directLogRows } = await env.DB.prepare(
          "SELECT * FROM exercise_logs ORDER BY program_epoch_days DESC, order_index ASC, set_number ASC;"
        ).all();

        const profilesMap = new Map();

        for (const row of results || []) {
          if (!row.data) continue;
          let parsed;
          try {
            parsed = JSON.parse(row.data);
          } catch {
            continue;
          }

          const body = parsed.body_profile || {};
          const userId = parsed.user_id ?? -1;
          const username = (parsed.username || body.username || (userId !== -1 ? `Athlete #${userId}` : "Default Profile")).trim();
          const key = username.toLowerCase();
          if (profilesMap.has(key)) continue;

          const weightKg = parseFloat(body.weight) || null;
          const heightCm = parseFloat(body.height) || null;
          const sex = body.sex || "Male";
          const age = parseInt(body.age, 10) || null;
          const explicitBf = parseFloat(body.body_fat_pct) || null;

          let bmi = null;
          if (weightKg > 0 && heightCm > 0) {
            const hM = heightCm / 100;
            bmi = weightKg / (hM * hM);
          }
          const bmiCategory =
            bmi === null ? "Not Set" :
            bmi < 18.5 ? "Underweight" :
            bmi < 25 ? "Normal" :
            bmi < 30 ? "Overweight" : "Obese";

          let bodyFatPct = explicitBf;
          let bodyFatEstimated = false;
          if (!bodyFatPct && bmi !== null) {
            const effAge = age || 28;
            const sexFactor = sex.toLowerCase() === "male" ? 1 : 0;
            bodyFatPct = Math.min(65, Math.max(3, 1.2 * bmi + 0.23 * effAge - 10.8 * sexFactor - 5.4));
            bodyFatEstimated = true;
          }

          const progEpoch = new Map();
          for (const p of parsed.programs || []) {
            if (p.id != null && p.date_epoch > 0) progEpoch.set(p.id, p.date_epoch);
          }

          const exercisesMap = new Map();
          let totalSetsLogged = 0;
          let totalVolumeMovedKg = 0;

          for (const s of parsed.sets || []) {
            const name = (s.exercise_name || "").trim();
            if (!name) continue;
            const dateEpoch = progEpoch.get(s.program_id) || 0;
            let loggedSets = [];
            if (s.completed_sets_json) {
              try {
                loggedSets = JSON.parse(s.completed_sets_json);
              } catch {
                loggedSets = [];
              }
            }
            if (!loggedSets.length && (s.completed_sets || 0) > 0) {
              for (let k = 1; k <= s.completed_sets; k++) {
                loggedSets.push({
                  setNumber: k,
                  reps: s.completed_reps || 0,
                  weightKg: s.completed_weight_kg || 0
                });
              }
            }

            const sessionMax = loggedSets.reduce((m, x) => Math.max(m, Number(x.weightKg) || 0), Number(s.completed_weight_kg) || 0);
            const sessionVol = loggedSets.reduce((sum, x) => sum + (Number(x.reps) || 0) * (Number(x.weightKg) || 0), 0);
            totalSetsLogged += loggedSets.length;
            totalVolumeMovedKg += sessionVol;

            if (!exercisesMap.has(name)) exercisesMap.set(name, []);
            exercisesMap.get(name).push({
              dateEpochDays: dateEpoch,
              targetSets: s.target_sets || 0,
              targetReps: s.target_reps || 0,
              completedSets: Math.max(loggedSets.length, s.completed_sets || 0),
              loggedSets,
              maxWeightKg: sessionMax,
              totalVolumeKg: sessionVol
            });
          }

          const exerciseProgress = Array.from(exercisesMap.entries()).map(([exerciseName, sessions]) => {
            const asc = [...sessions].sort((a, b) => a.dateEpochDays - b.dateEpochDays);
            const desc = [...sessions].sort((a, b) => b.dateEpochDays - a.dateEpochDays);
            return {
              exerciseName,
              sessionsCount: sessions.length,
              firstMaxWeightKg: asc[0]?.maxWeightKg || 0,
              latestMaxWeightKg: desc[0]?.maxWeightKg || 0,
              allTimeMaxWeightKg: Math.max(...sessions.map(x => x.maxWeightKg), 0),
              totalVolumeKg: sessions.reduce((sum, x) => sum + x.totalVolumeKg, 0),
              sessions: desc
            };
          }).sort((a, b) => b.totalVolumeKg - a.totalVolumeKg);

          profilesMap.set(key, {
            personKey: key,
            userId,
            username,
            weightKg,
            heightCm,
            sex,
            age,
            bmi,
            bmiCategory,
            bodyFatPct,
            bodyFatEstimated,
            syncedAt: row.timestamp || "Recent",
            totalWorkoutDays: (parsed.programs || []).length,
            totalSetsLogged,
            totalVolumeMovedKg,
            exerciseProgress
          });
        }

        // Enrich profilesMap from normalized exercise_logs table if any user logged sets directly without a snapshot yet
        const logsByPerson = new Map();
        for (const r of directLogRows || []) {
          const uname = (r.username || (r.user_id != null ? `Athlete #${r.user_id}` : "Default Profile")).trim();
          const pKey = uname.toLowerCase();
          if (!logsByPerson.has(pKey)) logsByPerson.set(pKey, { uname, userId: r.user_id ?? -1, rows: [] });
          logsByPerson.get(pKey).rows.push(r);
        }
        for (const [pKey, info] of logsByPerson.entries()) {
          if (profilesMap.has(pKey)) continue;
          const byExercise = new Map();
          const daySet = new Set();
          let totalSetsLogged = 0;
          let totalVolumeMovedKg = 0;
          for (const r of info.rows) {
            const exName = (r.exercise_name || "").trim();
            if (!exName) continue;
            const epoch = Number(r.program_epoch_days) || 0;
            if (epoch > 0) daySet.add(epoch);
            const sessKey = `${exName}::${epoch}::${r.order_index || 0}`;
            if (!byExercise.has(sessKey)) {
              byExercise.set(sessKey, {
                exerciseName: exName,
                dateEpochDays: epoch,
                targetSets: Number(r.target_sets) || 0,
                targetReps: Number(r.target_reps) || 0,
                loggedSets: []
              });
            }
            const setNum = Number(r.set_number) || 0;
            if (setNum > 0) {
              const reps = Number(r.completed_reps) || 0;
              const weightKg = Number(r.weight_kg) || 0;
              byExercise.get(sessKey).loggedSets.push({ setNumber: setNum, reps, weightKg });
              totalSetsLogged += 1;
              totalVolumeMovedKg += reps * weightKg;
            }
          }
          const exGroups = new Map();
          for (const sess of byExercise.values()) {
            sess.loggedSets.sort((a, b) => a.setNumber - b.setNumber);
            const maxWeightKg = sess.loggedSets.reduce((m, s) => Math.max(m, s.weightKg), 0);
            const totalVolumeKg = sess.loggedSets.reduce((sum, s) => sum + s.reps * s.weightKg, 0);
            if (!exGroups.has(sess.exerciseName)) exGroups.set(sess.exerciseName, []);
            exGroups.get(sess.exerciseName).push({
              dateEpochDays: sess.dateEpochDays,
              targetSets: sess.targetSets,
              targetReps: sess.targetReps,
              completedSets: sess.loggedSets.length,
              loggedSets: sess.loggedSets,
              maxWeightKg,
              totalVolumeKg
            });
          }
          const exerciseProgress = Array.from(exGroups.entries()).map(([exerciseName, sessions]) => {
            const asc = [...sessions].sort((a, b) => a.dateEpochDays - b.dateEpochDays);
            const desc = [...sessions].sort((a, b) => b.dateEpochDays - a.dateEpochDays);
            return {
              exerciseName,
              sessionsCount: sessions.length,
              firstMaxWeightKg: asc[0]?.maxWeightKg || 0,
              latestMaxWeightKg: desc[0]?.maxWeightKg || 0,
              allTimeMaxWeightKg: Math.max(...sessions.map(x => x.maxWeightKg), 0),
              totalVolumeKg: sessions.reduce((sum, x) => sum + x.totalVolumeKg, 0),
              sessions: desc
            };
          }).sort((a, b) => b.totalVolumeKg - a.totalVolumeKg);

          profilesMap.set(pKey, {
            personKey: pKey,
            userId: info.userId,
            username: info.uname,
            weightKg: null,
            heightCm: null,
            sex: "Male",
            age: null,
            bmi: null,
            bmiCategory: "Not Set",
            bodyFatPct: null,
            bodyFatIsEstimated: true,
            syncedAt: info.rows[0]?.updated_at || "Recent",
            totalWorkoutDays: daySet.size,
            totalSetsLogged,
            totalVolumeMovedKg,
            exerciseProgress
          });
        }

        return Response.json({
          success: true,
          generatedAt: new Date().toISOString(),
          profiles: Array.from(profilesMap.values())
        }, {
          headers: { "Access-Control-Allow-Origin": "*" }
        });
      } catch (err) {
        return Response.json({ success: false, error: err.message }, { status: 500 });
      }
    }

    return new Response(DASHBOARD_HTML, {
      headers: { "Content-Type": "text/html; charset=utf-8" }
    });
  }
};

const DASHBOARD_HTML = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>Elysium Cloud Platform — D1 Progress Dashboard</title>
  <style>
    :root {
      --bg: #0d0f14; --card: #171a23; --card2: #1f2430;
      --accent: #8a2be2; --accent2: #00f5d4; --text: #eef1f7; --muted: #9aa3b2; --border: #2c3242;
    }
    * { box-sizing: border-box; font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif; }
    body { margin: 0; background: var(--bg); color: var(--text); padding: 20px; }
    .wrap { max-width: 1080px; margin: 0 auto; }
    header { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 18px; }
    h1 { margin: 0; font-size: 1.5rem; }
    .badge { padding: 6px 12px; border-radius: 999px; font-size: 0.8rem; background: rgba(138,43,226,.2); border: 1px solid var(--accent); }
    .profiles { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
    .chip { cursor: pointer; padding: 8px 14px; border-radius: 999px; border: 1px solid var(--border); background: var(--card); color: var(--text); font-weight: 600; }
    .chip.active { background: var(--accent); border-color: #fff; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; margin: 14px 0; }
    .stat { background: var(--card2); border: 1px solid var(--border); border-radius: 14px; padding: 14px; text-align: center; }
    .stat .lbl { color: var(--muted); font-size: 0.78rem; margin-bottom: 4px; }
    .stat .val { font-size: 1.35rem; font-weight: 800; }
    .card { background: var(--card); border: 1px solid var(--border); border-radius: 16px; padding: 16px; margin-bottom: 14px; }
    .ex-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 10px; }
    .pr { color: var(--accent2); font-weight: 800; }
    .session { background: var(--card2); border-radius: 10px; padding: 10px; margin-top: 8px; }
    .sets { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
    .set-pill { background: rgba(138,43,226,.22); border: 1px solid rgba(138,43,226,.55); border-radius: 8px; padding: 4px 8px; font-size: 0.82rem; font-weight: 600; }
    .bars { display: flex; align-items: flex-end; gap: 6px; height: 42px; margin: 8px 0; }
    .bar { flex: 1; background: linear-gradient(180deg, var(--accent2), var(--accent)); border-radius: 4px 4px 0 0; min-height: 6px; }
    button.refresh { background: var(--accent); color: #fff; border: 0; border-radius: 10px; padding: 8px 14px; font-weight: 700; cursor: pointer; }
  </style>
</head>
<body>
<div class="wrap">
  <header>
    <div>
      <h1>Elysium Training — Cloudflare D1 Platform</h1>
      <div id="status" style="color:var(--muted);font-size:.85rem;">Loading D1 profiles...</div>
    </div>
    <button class="refresh" onclick="loadData()">Refresh D1 Data</button>
  </header>
  <div id="profiles" class="profiles"></div>
  <div id="content"></div>
</div>
<script>
let allProfiles = [];
let selectedKey = null;

function fmtDate(epochDays) {
  if (!epochDays) return "Logged Session";
  return new Date(epochDays * 86400000).toLocaleDateString();
}

function render() {
  const pBox = document.getElementById("profiles");
  const cBox = document.getElementById("content");
  if (!allProfiles.length) {
    pBox.innerHTML = "";
    cBox.innerHTML = '<div class="card">No D1 snapshots found yet. Sync from the Android app first.</div>';
    return;
  }
  const active = allProfiles.find(p => p.personKey === selectedKey) || allProfiles[0];
  selectedKey = active.personKey;

  pBox.innerHTML = allProfiles.map(p =>
    \`<button class="chip \${p.personKey === active.personKey ? 'active' : ''}" onclick="selectPerson('\${p.personKey}')">\${p.username}</button>\`
  ).join("");

  const bfLabel = active.bodyFatEstimated ? "Body Fat % (Est)" : "Body Fat %";
  let html = \`
    <div class="card">
      <div class="ex-head">
        <div>
          <h2 style="margin:0">\${active.username}</h2>
          <div style="color:var(--muted);font-size:.85rem">\${active.sex}\${active.age ? ' • Age ' + active.age : ''} • Synced: \${active.syncedAt}</div>
        </div>
        <span class="badge">\${active.bmiCategory}</span>
      </div>
      <div class="grid">
        <div class="stat"><div class="lbl">Height</div><div class="val">\${active.heightCm ? active.heightCm.toFixed(0) + ' cm' : '—'}</div></div>
        <div class="stat"><div class="lbl">Weight</div><div class="val">\${active.weightKg ? active.weightKg.toFixed(1) + ' kg' : '—'}</div></div>
        <div class="stat"><div class="lbl">BMI</div><div class="val">\${active.bmi ? active.bmi.toFixed(1) : '—'}</div></div>
        <div class="stat"><div class="lbl">\${bfLabel}</div><div class="val">\${active.bodyFatPct ? active.bodyFatPct.toFixed(1) + '%' : '—'}</div></div>
      </div>
      <div class="grid">
        <div class="stat"><div class="lbl">Workout Days</div><div class="val">\${active.totalWorkoutDays}</div></div>
        <div class="stat"><div class="lbl">Sets Logged</div><div class="val">\${active.totalSetsLogged}</div></div>
        <div class="stat"><div class="lbl">Volume Moved</div><div class="val">\${Math.round(active.totalVolumeMovedKg).toLocaleString()} kg</div></div>
      </div>
    </div>
    <h3>Exercise Progress & Logged Weights / Reps (\${active.exerciseProgress.length})</h3>
  \`;

  for (const ex of active.exerciseProgress) {
    const delta = ex.latestMaxWeightKg - ex.firstMaxWeightKg;
    const deltaStr = ex.sessionsCount <= 1 ? "Baseline" : (delta > 0 ? \`+\${delta.toFixed(1)} kg\` : \`\${delta.toFixed(1)} kg\`);
    const barsData = [...ex.sessions].reverse().slice(-8);
    const maxW = Math.max(...barsData.map(s => s.maxWeightKg), 1);

    html += \`
      <div class="card">
        <div class="ex-head">
          <div>
            <strong>\${ex.exerciseName}</strong>
            <div style="color:var(--muted);font-size:.82rem">\${ex.sessionsCount} session(s) • \${Math.round(ex.totalVolumeKg).toLocaleString()} kg total vol</div>
          </div>
          <div style="text-align:right">
            <div class="pr">PR: \${ex.allTimeMaxWeightKg.toFixed(1)} kg</div>
            <div style="color:var(--muted);font-size:.78rem">\${deltaStr}</div>
          </div>
        </div>
        <div class="bars">
          \${barsData.map(b => \`<div class="bar" style="height:\${Math.max(15, (b.maxWeightKg / maxW) * 100)}%"></div>\`).join("")}
        </div>
        \${ex.sessions.slice(0, 6).map(s => \`
          <div class="session">
            <div style="display:flex;justify-content:space-between;font-size:.82rem">
              <strong>\${fmtDate(s.dateEpochDays)}</strong>
              <span style="color:var(--muted)">\${s.completedSets}/\${s.targetSets} sets • \${Math.round(s.totalVolumeKg)} kg vol</span>
            </div>
            <div class="sets">
              \${(s.loggedSets || []).map(st => \`<span class="set-pill">S\${st.setNumber}: \${Number(st.weightKg).toFixed(1)}kg × \${st.reps}</span>\`).join("")}
            </div>
          </div>
        \`).join("")}
      </div>
    \`;
  }
  cBox.innerHTML = html;
}

window.selectPerson = function(key) {
  selectedKey = key;
  render();
};

window.loadData = async function() {
  const statusEl = document.getElementById("status");
  try {
    const res = await fetch("/api/dashboard");
    const json = await res.json();
    if (!json.success) throw new Error(json.error || "D1 error");
    allProfiles = json.profiles || [];
    localStorage.setItem("elysium_d1_cache", JSON.stringify(allProfiles));
    statusEl.textContent = "Live from Cloudflare D1 • Updated " + new Date().toLocaleTimeString();
    render();
  } catch (e) {
    const cached = localStorage.getItem("elysium_d1_cache");
    if (cached) {
      allProfiles = JSON.parse(cached);
      statusEl.textContent = "Offline Mode — Showing cached D1 progress";
      render();
    } else {
      statusEl.textContent = "Could not load D1: " + e.message;
    }
  }
};

loadData();
</script>
</body>
</html>`;
