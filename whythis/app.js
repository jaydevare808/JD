const SUPABASE_URL = 'https://rxxbercdedehtjfyppdb.supabase.co';
const SUPABASE_KEY = 'sb_publishable_rFr0WoZvANcnDfhg2VPTxw_s7U_fNXY';
const { createClient } = supabase;
const db = createClient(SUPABASE_URL, SUPABASE_KEY, { auth: { persistSession: false } });

const state = { topics: [], categories: [], active: 'All', query: '', loading: true };
const $ = (s) => document.querySelector(s);
const esc = (v='') => String(v).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c]));

const toneGlyph = { violet: 'N', cyan: '◌', amber: '404', pink: '▦', green: '29', blue: '✦' };

function formatDate(date = new Date()) {
  return new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'long', year: 'numeric' }).format(date);
}

function setStatus(message) { const el = $('#statusText'); if (el) el.textContent = message; }

function renderFilters() {
  const items = ['All', ...state.categories.map(c => c.name)];
  $('#filters').innerHTML = items.map(item => `<button class="chip ${state.active===item?'active':''}" data-filter="${esc(item)}">${esc(item)}</button>`).join('');
  $('#filters').querySelectorAll('.chip').forEach(b => b.addEventListener('click', () => { state.active = b.dataset.filter; renderFilters(); renderTopics(); }));
}

function visibleTopics() {
  const q = state.query.trim().toLowerCase();
  return state.topics.filter(t => {
    const categoryOk = state.active === 'All' || t.category?.name === state.active;
    const text = `${t.title} ${t.deck} ${t.tag} ${t.category?.name || ''}`.toLowerCase();
    return categoryOk && (!q || text.includes(q));
  });
}

function renderTopics() {
  const data = visibleTopics();
  $('#topicCount').textContent = `${data.length} explanation${data.length === 1 ? '' : 's'}`;
  $('#topicGrid').innerHTML = data.map((t) => `
    <article class="topic-card ${esc(t.tone || 'violet')}" data-slug="${esc(t.slug)}">
      <div class="topic-meta"><span>${esc(t.tag)}</span><span>${esc(t.reading_minutes)} min</span></div>
      <div class="topic-symbol" aria-hidden="true">${esc(toneGlyph[t.tone] || '✦')}</div>
      <div class="topic-copy"><h3>${esc(t.title)}</h3><p>${esc(t.deck)}</p></div>
      <div class="topic-footer"><span>Read the explanation</span><span>↗</span></div>
    </article>`).join('');
  $('#emptyState').hidden = data.length > 0;
  $('#topicGrid').querySelectorAll('.topic-card').forEach(c => c.addEventListener('click', () => openTopic(c.dataset.slug)));
}

function renderCategories() {
  $('#categoryGrid').innerHTML = state.categories.map(c => `
    <button class="category-card" data-category="${esc(c.name)}">
      <span class="category-icon">${esc(c.icon)}</span><span><b>${esc(c.name)}</b><small>${esc(c.description || '')}</small></span><span>→</span>
    </button>`).join('');
  $('#categoryGrid').querySelectorAll('.category-card').forEach(b => b.addEventListener('click', () => {
    state.active = b.dataset.category; state.query = ''; $('#searchInput').value = ''; renderFilters(); renderTopics(); $('#explore').scrollIntoView({behavior:'smooth'});
  }));
}

function renderToday() {
  const featured = state.topics.filter(t => t.featured).slice(0, 5);
  const today = featured.length ? featured : state.topics.slice(0,5);
  $('#todayDate').textContent = formatDate();
  $('#todayList').innerHTML = today.map((t, i) => `
    <button class="today-item" data-slug="${esc(t.slug)}">
      <span class="today-num">${String(i+1).padStart(2,'0')}</span>
      <span class="today-title">${esc(t.title)}</span>
      <span class="today-type">${esc(t.tag)}</span><span class="today-arrow">↗</span>
    </button>`).join('');
  $('#todayList').querySelectorAll('.today-item').forEach(b => b.addEventListener('click', () => openTopic(b.dataset.slug)));
}

function renderState() {
  renderFilters(); renderTopics(); renderCategories(); renderToday();
  $('#topicSearchHint').textContent = state.topics.length ? `Search ${state.topics.length} curated explanations` : 'Search the knowledge library';
  setStatus('Live knowledge library');
}

function openTopic(slug) {
  const t = state.topics.find(x => x.slug === slug); if (!t) return;
  const related = (t.next_questions || []).map(q => `<button class="next-question" data-query="${esc(q)}">${esc(q)} <span>→</span></button>`).join('');
  $('#modalContent').innerHTML = `
    <div class="modal-top"><span class="pill">${esc(t.category?.name || '')}</span><span class="verified">Verified ${t.verified_at ? new Date(t.verified_at).toLocaleDateString('en-IN') : 'recently'}</span></div>
    <div class="modal-tag">${esc(t.tag)} · ${esc(t.reading_minutes)} min</div>
    <h2>${esc(t.title)}</h2>
    <p class="modal-lede">${esc(t.short_answer)}</p>
    <section class="answer-block"><span class="block-label">THE MECHANISM</span><p>${esc(t.mechanism || t.body)}</p></section>
    <section class="answer-block"><span class="block-label">WHAT TO REMEMBER</span><p>${esc(t.remember || 'Keep the core idea, then follow the next question.')}</p></section>
    <section class="answer-block misconception"><span class="block-label">COMMON MIX-UP</span><p>${esc(t.misconception || 'None listed yet.')}</p></section>
    <section class="source-block"><span class="block-label">SOURCE TRAIL</span><div><b>${esc(t.source_name || 'Curated by WHYTHIS')}</b>${t.source_url ? `<a href="${esc(t.source_url)}" target="_blank" rel="noreferrer">Open source ↗</a>` : ''}</div></section>
    <section class="next-block"><span class="block-label">GO ONE STEP DEEPER</span>${related || '<p class="muted">More related questions are being added.</p>'}</section>`;
  $('#modal').showModal();
  $('#modalContent').querySelectorAll('.next-question').forEach(b => b.addEventListener('click', () => { $('#modal').close(); setQuery(b.dataset.query); }));
}

function setQuery(value) {
  state.query = value || ''; state.active = 'All'; $('#searchInput').value = state.query; renderFilters(); renderTopics(); $('#explore').scrollIntoView({behavior:'smooth'});
}

async function loadData() {
  try {
    const [topicsRes, categoriesRes] = await Promise.all([
      db.from('topics').select('id,slug,title,deck,short_answer,body,why_it_matters,remember,mechanism,misconception,next_questions,tag,reading_minutes,tone,source_name,source_url,verified_at,featured,category:categories(name,slug,icon,description)').eq('published', true).order('featured', {ascending:false}).order('updated_at',{ascending:false}),
      db.from('categories').select('id,name,slug,icon,description').order('name')
    ]);
    if (topicsRes.error) throw topicsRes.error; if (categoriesRes.error) throw categoriesRes.error;
    state.topics = topicsRes.data || []; state.categories = categoriesRes.data || []; state.loading = false; renderState();
  } catch (e) {
    console.error(e); state.loading = false; setStatus('Offline fallback'); $('#topicGrid').innerHTML = '<div class="offline">The library could not be reached. Refresh once the connection is available.</div>'; $('#emptyState').hidden = true;
  }
}

async function subscribe() {
  const email = $('#email').value.trim().toLowerCase();
  const note = $('#signupNote'); if (!email) return;
  const btn = $('#signupButton'); btn.disabled = true; btn.textContent = 'Saving…';
  const { error } = await db.from('newsletter_subscribers').insert({ email });
  if (error && !String(error.message).includes('duplicate')) { note.textContent = 'Could not save that address. Please try again.'; btn.disabled = false; btn.textContent = 'Join the daily five'; return; }
  note.textContent = 'You’re on the list. The daily five will land when the production email workflow is connected.'; btn.textContent = 'Joined ✓';
}

$('#searchForm').addEventListener('submit', e => { e.preventDefault(); setQuery($('#searchInput').value); });
$('#searchInput').addEventListener('input', e => { state.query=e.target.value; renderTopics(); });
document.querySelectorAll('[data-query]').forEach(b => b.addEventListener('click', () => setQuery(b.dataset.query)));
document.querySelectorAll('[data-scroll]').forEach(b => b.addEventListener('click', () => $(b.dataset.scroll).scrollIntoView({behavior:'smooth'})));
$('#signup').addEventListener('submit', e => { e.preventDefault(); subscribe(); });
$('#modal').addEventListener('click', e => { if (e.target === $('#modal')) $('#modal').close(); });
$('#year').textContent = new Date().getFullYear();
loadData();
