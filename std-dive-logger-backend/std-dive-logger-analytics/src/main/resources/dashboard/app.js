const byId = id => document.getElementById(id);
let snapshot;
let refreshing = false;
let submitting = false;
const time = value => value == null ? '—' : new Date(value).toLocaleString();
const active = run => ['QUEUED', 'RUNNING'].includes(run.status);
function node(tag, text, className) {
  const element = document.createElement(tag);
  if (text != null) element.textContent = text;
  if (className) element.className = className;
  return element;
}
function notice(message, error = false) { byId('notice').textContent = message; byId('notice').className = error ? 'error' : ''; }
function renderHistory() {
  const filter = byId('filter').value;
  const rows = snapshot.runs.filter(run => filter === 'all' || (filter === 'active' ? active(run) : ['FAILED', 'INTERRUPTED'].includes(run.status)));
  byId('history').replaceChildren(...rows.map(run => {
    const row = node('tr');
    const label = snapshot.jobs.find(job => job.id === run.job)?.label || run.job;
    row.append(node('td', label));
    const status = node('td'); status.append(node('span', run.status, `badge ${run.status}`)); row.append(status);
    const seconds = run.startedAt == null ? null : Math.max(0, ((run.finishedAt ?? snapshot.now) - run.startedAt) / 1000);
    row.append(node('td', run.trigger === 'MANUAL' ? 'Manual' : 'Scheduled'), node('td', time(run.queuedAt)), node('td', seconds == null ? '—' : `${seconds.toFixed(1)} s`), node('td', run.errorType || `Run #${run.id}`));
    return row;
  }));
  byId('empty').hidden = rows.length > 0;
}
async function refresh() {
  if (refreshing || submitting) return;
  refreshing = true;
  try {
    const response = await fetch('/ops/api/status', {cache: 'no-store'});
    if (!response.ok) throw new Error('Status is temporarily unavailable.');
    snapshot = await response.json();
    byId('running').textContent = snapshot.runs.filter(run => run.status === 'RUNNING').length;
    byId('queued').textContent = snapshot.runs.filter(run => run.status === 'QUEUED').length;
    byId('failed').textContent = snapshot.runs.filter(run => ['FAILED','INTERRUPTED'].includes(run.status)).length;
    byId('updated').textContent = new Date(snapshot.now).toLocaleTimeString();
    byId('jobs').replaceChildren(...snapshot.jobs.map(job => {
      const card = node('article', null, 'job');
      card.append(node('h3', job.label), node('p', job.description), node('p', snapshot.schedulingEnabled ? `Next scheduled: ${time(job.nextRun)}` : 'Scheduling and worker disabled', 'next'));
      const busy = snapshot.runs.some(run => run.job === job.id && active(run));
      const button = node('button', busy ? 'Already queued / running' : 'Run now');
      button.disabled = busy || !snapshot.schedulingEnabled;
      button.addEventListener('click', () => start(job, button)); card.append(button); return card;
    }));
    renderHistory();
    notice(snapshot.schedulingEnabled ? 'Connected · refreshes every 5 seconds' : 'Worker disabled: manual submissions are unavailable.');
  } catch (error) { notice(`${error.message} Displayed data may be stale.`, true); }
  finally { refreshing = false; }
}
async function start(job, button) {
  if (submitting || !confirm(`Queue “${job.label}”? ${job.description}.`)) return;
  submitting = true; button.disabled = true;
  try {
    const tokenResponse = await fetch('/ops/api/csrf', {cache:'no-store'});
    if (!tokenResponse.ok) throw new Error('Could not prepare a secure request.');
    const csrf = await tokenResponse.json();
    const response = await fetch(`/ops/api/jobs/${job.id}/runs`, {method:'POST',headers:{[csrf.header]:csrf.token}});
    if (!response.ok && response.status !== 409) throw new Error(`Job submission failed (${response.status}).`);
    notice((await response.json()).message);
  } catch (error) { notice(error.message, true); }
  finally { submitting = false; button.disabled = false; }
}
byId('filter').addEventListener('change', () => { if (snapshot) renderHistory(); });
refresh();
setInterval(() => { if (!document.hidden) refresh(); }, 5000);
