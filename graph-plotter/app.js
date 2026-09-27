const CANVAS_W = 1106;
const CANVAS_H = 1536;
const COLS = 17;
const ROWS = 27;
const image = new Image();
image.src = 'template.svg';

const el = id => document.getElementById(id);
const canvas = el('graphCanvas');
const ctx = canvas.getContext('2d');
const viewport = el('canvasViewport');
const wrap = el('canvasWrap');
const overlay = el('calibrationOverlay');
const message = el('message');

let zoom = 1;
let originMode = 'auto';
let calibrationEdit = false;
let currentOrigin = { x: 2, y: 2 };
let lastGraph = null;

const calibration = {
  tl: { x: 40, y: 38 },
  tr: { x: 1007, y: 38 },
  br: { x: 1007, y: 1497 },
  bl: { x: 40, y: 1497 }
};

const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

function showMessage(text, error = false) {
  message.textContent = text;
  message.classList.toggle('hidden', !text);
  message.classList.toggle('error', error);
}

function parseValues(text) {
  const tokens = String(text)
    .replace(/[\u2012\u2013\u2014]/g, '-')
    .split(/[\s,;]+/)
    .map(s => s.trim())
    .filter(Boolean);
  const values = tokens.map(Number);
  if (values.some(v => !Number.isFinite(v))) throw new Error('One or more readings are not valid numbers.');
  return values;
}

function readData() {
  const xs = parseValues(el('xValues').value);
  const ys = parseValues(el('yValues').value);
  if (!xs.length || !ys.length) throw new Error('Enter X and Y readings first.');
  if (xs.length !== ys.length) throw new Error(`X has ${xs.length} values but Y has ${ys.length}. They must match.`);
  return xs.map((x, i) => ({ x, y: ys[i] }));
}

function numberInput(id) {
  const n = Number(el(id).value);
  if (!Number.isFinite(n)) throw new Error('Scale and calibration values must be numbers.');
  return n;
}

function solveHomography(src, dst) {
  const A = [];
  const b = [];
  for (let i = 0; i < 4; i++) {
    const [u, v] = src[i];
    const [x, y] = dst[i];
    A.push([u, v, 1, 0, 0, 0, -u*x, -v*x]); b.push(x);
    A.push([0, 0, 0, u, v, 1, -u*y, -v*y]); b.push(y);
  }
  for (let col = 0; col < 8; col++) {
    let pivot = col;
    for (let r = col + 1; r < 8; r++) if (Math.abs(A[r][col]) > Math.abs(A[pivot][col])) pivot = r;
    if (Math.abs(A[pivot][col]) < 1e-10) throw new Error('Calibration points are degenerate.');
    [A[col], A[pivot]] = [A[pivot], A[col]];
    [b[col], b[pivot]] = [b[pivot], b[col]];
    const div = A[col][col];
    for (let c = col; c < 8; c++) A[col][c] /= div;
    b[col] /= div;
    for (let r = 0; r < 8; r++) {
      if (r === col) continue;
      const f = A[r][col];
      if (!f) continue;
      for (let c = col; c < 8; c++) A[r][c] -= f * A[col][c];
      b[r] -= f * b[col];
    }
  }
  return [...b, 1];
}

function mapPoint(H, u, v) {
  const d = H[6] * u + H[7] * v + 1;
  return { x: (H[0] * u + H[1] * v + H[2]) / d, y: (H[3] * u + H[4] * v + H[5]) / d };
}

function getHomography() {
  return solveHomography([[0,0],[1,0],[1,1],[0,1]], [calibration.tl, calibration.tr, calibration.br, calibration.bl]);
}

function normalizedDataPoint(H, p, xScale, yScale, origin) {
  const u = (origin.x + p.x / xScale) / COLS;
  const v = 1 - (origin.y + p.y / yScale) / ROWS;
  return mapPoint(H, u, v);
}

function chooseOrigin(data, sx, sy) {
  const marginX = 1, marginY = 1;
  const minX = Math.min(...data.map(d => d.x / sx));
  const maxX = Math.max(...data.map(d => d.x / sx));
  const minY = Math.min(...data.map(d => d.y / sy));
  const maxY = Math.max(...data.map(d => d.y / sy));
  const validX = [], validY = [];
  for (let ox = 0; ox <= COLS; ox++) if (ox + minX >= marginX - 1e-9 && ox + maxX <= COLS - marginX + 1e-9) validX.push(ox);
  for (let oy = 0; oy <= ROWS; oy++) if (oy + minY >= marginY - 1e-9 && oy + maxY <= ROWS - marginY + 1e-9) validY.push(oy);
  if (!validX.length || !validY.length) throw new Error('The selected scale does not fit all readings on this 17 × 27 major-square page. Reduce the scale value or use a larger graph sheet.');
  const targetX = (COLS - (minX + maxX)) / 2;
  const targetY = (ROWS - (minY + maxY)) / 2;
  return {
    x: validX.reduce((a,b) => Math.abs(a-targetX) < Math.abs(b-targetX) ? a : b),
    y: validY.reduce((a,b) => Math.abs(a-targetY) < Math.abs(b-targetY) ? a : b)
  };
}

function bestFit(data) {
  const n = data.length;
  if (n < 2) return null;
  const sx = data.reduce((s,p) => s+p.x, 0);
  const sy = data.reduce((s,p) => s+p.y, 0);
  const sxx = data.reduce((s,p) => s+p.x*p.x, 0);
  const sxy = data.reduce((s,p) => s+p.x*p.y, 0);
  const denom = n*sxx - sx*sx;
  if (Math.abs(denom) < 1e-12) return null;
  const m = (n*sxy - sx*sy)/denom;
  const b = (sy - m*sx)/n;
  const meanY = sy/n;
  const ssTot = data.reduce((s,p) => s + (p.y-meanY)**2, 0);
  const ssRes = data.reduce((s,p) => s + (p.y-(m*p.x+b))**2, 0);
  return { m, b, r2: ssTot ? 1 - ssRes/ssTot : 1 };
}

function getPlotRange(origin, sx, sy) {
  return { minX: -origin.x*sx, maxX: (COLS-origin.x)*sx, minY: -origin.y*sy, maxY: (ROWS-origin.y)*sy };
}

function drawAxis(H, axis) {
  const { minX, maxX, minY, maxY } = axis;
  const left = normalizedDataPoint(H,{x:minX,y:0},axis.sx,axis.sy,axis.origin);
  const right = normalizedDataPoint(H,{x:maxX,y:0},axis.sx,axis.sy,axis.origin);
  const bottom = normalizedDataPoint(H,{x:0,y:minY},axis.sx,axis.sy,axis.origin);
  const top = normalizedDataPoint(H,{x:0,y:maxY},axis.sx,axis.sy,axis.origin);
  ctx.save();
  ctx.strokeStyle = 'rgba(9,28,44,.88)';
  ctx.fillStyle = 'rgba(9,28,44,.9)';
  ctx.lineWidth = 2.1;
  ctx.beginPath(); ctx.moveTo(left.x,left.y); ctx.lineTo(right.x,right.y); ctx.stroke();
  ctx.beginPath(); ctx.moveTo(bottom.x,bottom.y); ctx.lineTo(top.x,top.y); ctx.stroke();
  const arrow = 8;
  ctx.beginPath(); ctx.moveTo(right.x,right.y); ctx.lineTo(right.x-arrow,right.y-4); ctx.lineTo(right.x-arrow,right.y+4); ctx.closePath(); ctx.fill();
  ctx.beginPath(); ctx.moveTo(top.x,top.y); ctx.lineTo(top.x-4,top.y+arrow); ctx.lineTo(top.x+4,top.y+arrow); ctx.closePath(); ctx.fill();
  ctx.restore();
}

function drawBestFit(H, data, axis, fit) {
  if (!fit) return;
  let lo = axis.minX, hi = axis.maxX;
  if (fit.m !== 0) {
    const x1 = (Math.min(...data.map(d=>d.y)) - fit.b) / fit.m;
    const x2 = (Math.max(...data.map(d=>d.y)) - fit.b) / fit.m;
    if (Number.isFinite(x1) && Number.isFinite(x2)) { lo = Math.max(axis.minX, Math.min(x1,x2)); hi = Math.min(axis.maxX, Math.max(x1,x2)); }
  }
  const p1 = normalizedDataPoint(H,{x:lo,y:fit.m*lo+fit.b},axis.sx,axis.sy,axis.origin);
  const p2 = normalizedDataPoint(H,{x:hi,y:fit.m*hi+fit.b},axis.sx,axis.sy,axis.origin);
  ctx.save(); ctx.strokeStyle='#d22f3f'; ctx.lineWidth=3.2; ctx.beginPath(); ctx.moveTo(p1.x,p1.y); ctx.lineTo(p2.x,p2.y); ctx.stroke(); ctx.restore();
}

function drawData(H, data, axis, style, pointSize) {
  const pts = data.map(p => normalizedDataPoint(H,p,axis.sx,axis.sy,axis.origin));
  ctx.save();
  if (style === 'joined') {
    ctx.strokeStyle='#087bb5'; ctx.lineWidth=2.4; ctx.beginPath();
    pts.forEach((p,i)=>i?ctx.lineTo(p.x,p.y):ctx.moveTo(p.x,p.y)); ctx.stroke();
  }
  ctx.fillStyle='#0a72a5';
  for (const p of pts) { ctx.beginPath(); ctx.arc(p.x,p.y,pointSize,0,Math.PI*2); ctx.fill(); ctx.strokeStyle='white'; ctx.lineWidth=1.2; ctx.stroke(); }
  ctx.restore();
}

function formatNum(n) {
  if (Math.abs(n) < 1e-9) return '0';
  return Number(n.toFixed(6)).toString();
}

function drawScaleAndEquation(H, axis, fit) {
  ctx.save();
  ctx.fillStyle='rgba(5,22,34,.88)';
  ctx.font='600 16px system-ui, sans-serif';
  const originPx = normalizedDataPoint(H,{x:0,y:0},axis.sx,axis.sy,axis.origin);
  ctx.fillText(`X: ${formatNum(axis.sx)} / sq   Y: ${formatNum(axis.sy)} / sq`, clamp(originPx.x+18,30,CANVAS_W-360), clamp(originPx.y-18,28,CANVAS_H-20));
  if (fit) ctx.fillText(`Best fit: y = ${formatNum(fit.m)}x ${fit.b >= 0 ? '+' : '−'} ${formatNum(Math.abs(fit.b))}   |   R² = ${formatNum(fit.r2)}`, 58, 72);
  ctx.restore();
}

function render() {
  if (!image.complete) return;
  try {
    const data=readData();
    const sx=numberInput('xScale'), sy=numberInput('yScale');
    const pointSize=clamp(numberInput('pointSize'),1,12);
    if (sx===0 || sy===0) throw new Error('Scale values must be non-zero.');
    const origin=originMode==='auto'?chooseOrigin(data,sx,sy):{x:numberInput('originX'),y:numberInput('originY')};
    if(origin.x<0||origin.x>COLS||origin.y<0||origin.y>ROWS) throw new Error('Manual axis position is outside the graph paper.');
    const H=getHomography();
    ctx.clearRect(0,0,CANVAS_W,CANVAS_H);
    ctx.drawImage(image,0,0,CANVAS_W,CANVAS_H);
    const axis={...getPlotRange(origin,sx,sy),sx,sy,origin};
    drawAxis(H,axis);
    const style=el('plotStyle').value;
    const fit=style==='bestfit'?bestFit(data):null;
    if(style==='bestfit'&&!fit) throw new Error('Best-fit line needs at least two points with different X values.');
    drawData(H,data,axis,style==='bestfit'?'points':style,pointSize);
    if(fit) drawBestFit(H,data,axis,fit);
    drawScaleAndEquation(H,axis,fit);
    currentOrigin=origin; lastGraph={data,axis,fit,style};
    el('statusText').textContent='Ready'; el('statusText').style.color='var(--success)';
    el('stats').innerHTML=`<div class="stat"><b>${data.length}</b><span>readings</span></div><div class="stat"><b>${origin.x}, ${origin.y}</b><span>origin (major sq)</span></div>${fit?`<div class="stat"><b>${formatNum(fit.m)}</b><span>slope</span></div><div class="stat"><b>${formatNum(fit.r2)}</b><span>R²</span></div>`:''}`;
    showMessage('');
  } catch(err) {
    el('statusText').textContent='Check input'; el('statusText').style.color='var(--danger)';
    showMessage(err.message||'Could not draw the graph.',true);
  }
}

function syncCalibrationFields(){ for(const key of ['tl','tr','br','bl']){el(key+'X').value=calibration[key].x;el(key+'Y').value=calibration[key].y;} }
function readCalibrationFields(){ for(const key of ['tl','tr','br','bl']){calibration[key].x=Number(el(key+'X').value);calibration[key].y=Number(el(key+'Y').value);} }
function positionHandles(){ for(const key of ['tl','tr','br','bl']){const node=overlay.querySelector(`[data-handle="${key}"]`);const p=calibration[key];node.style.left=`${p.x}px`;node.style.top=`${p.y}px`;} }

function setZoom(next){
  zoom=clamp(next,.45,2);
  wrap.style.width=`${CANVAS_W*zoom}px`; wrap.style.height=`${CANVAS_H*zoom}px`;
  canvas.style.width=`${CANVAS_W*zoom}px`; canvas.style.height=`${CANVAS_H*zoom}px`;
  overlay.style.transform=`scale(${zoom})`; overlay.style.transformOrigin='0 0';
  el('zoomText').textContent=`${Math.round(zoom*100)}%`;
}
function fitToViewport(){ const pad=42; setZoom(Math.min((viewport.clientWidth-pad)/CANVAS_W,(viewport.clientHeight-pad)/CANVAS_H,1)); }

function resetAll(){
  el('xValues').value=''; el('yValues').value=''; el('xScale').value='1'; el('yScale').value='1';
  el('plotStyle').value='bestfit'; el('pointSize').value='4';
  originMode='auto'; document.querySelectorAll('.segment').forEach(b=>b.classList.toggle('active',b.dataset.origin==='auto'));
  el('manualOrigin').classList.remove('show');
  calibration.tl={x:40,y:38}; calibration.tr={x:1007,y:38}; calibration.br={x:1007,y:1497}; calibration.bl={x:40,y:1497};
  syncCalibrationFields(); positionHandles(); render();
}

for(const id of ['xValues','yValues','xScale','yScale','plotStyle','pointSize','originX','originY']) el(id).addEventListener('input',render);
document.querySelectorAll('.segment').forEach(btn=>btn.addEventListener('click',()=>{originMode=btn.dataset.origin;document.querySelectorAll('.segment').forEach(b=>b.classList.toggle('active',b===btn));el('manualOrigin').classList.toggle('show',originMode==='manual');render();}));
el('exportBtn').addEventListener('click',()=>{render();const link=document.createElement('a');link.download='graph-paper-plot.png';link.href=canvas.toDataURL('image/png');link.click();});
el('printBtn').addEventListener('click',()=>{render();window.print();});
el('resetBtn').addEventListener('click',resetAll);
el('zoomIn').addEventListener('click',()=>setZoom(zoom+.1));
el('zoomOut').addEventListener('click',()=>setZoom(zoom-.1));
el('fitBtn').addEventListener('click',fitToViewport);
el('calibrateBtn').addEventListener('click',()=>{
  calibrationEdit=!calibrationEdit; overlay.classList.toggle('hidden',!calibrationEdit);
  el('calibrateBtn').textContent=calibrationEdit?'Done':'Edit corners'; el('calibrationFields').classList.toggle('locked',!calibrationEdit);
  if(calibrationEdit){positionHandles();showMessage('Calibration mode: drag the four cyan handles to the paper corners.');}
  else{readCalibrationFields();render();showMessage('Calibration saved for this session.');}
});
['tl','tr','br','bl'].forEach(key=>{
  const node=overlay.querySelector(`[data-handle="${key}"]`); let drag=false;
  const move=ev=>{if(!drag)return;const rect=wrap.getBoundingClientRect();const x=clamp((ev.clientX-rect.left)/zoom,0,CANVAS_W);const y=clamp((ev.clientY-rect.top)/zoom,0,CANVAS_H);calibration[key]={x,y};el(key+'X').value=Math.round(x);el(key+'Y').value=Math.round(y);node.style.left=`${x}px`;node.style.top=`${y}px`;render();};
  node.addEventListener('pointerdown',e=>{drag=true;node.setPointerCapture?.(e.pointerId);e.preventDefault();});
  node.addEventListener('pointerup',()=>{drag=false}); node.addEventListener('pointercancel',()=>{drag=false}); node.addEventListener('pointermove',move);
});
['tlX','tlY','trX','trY','brX','brY','blX','blY'].forEach(id=>el(id).addEventListener('input',()=>{if(!calibrationEdit)return;readCalibrationFields();positionHandles();render();}));
image.addEventListener('load',()=>{el('sizeText').textContent=`${image.naturalWidth} × ${image.naturalHeight} px`;fitToViewport();render();});
window.addEventListener('resize',()=>{if(zoom<=1.01)fitToViewport();});