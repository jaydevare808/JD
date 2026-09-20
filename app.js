const cfg=window.BIOPROJECT_SUPABASE||{};
const sb=window.supabase?.createClient?.(cfg.url,cfg.anonKey);
const $=s=>document.querySelector(s);
let projects=[],session=null,isAdmin=false,authListener=null;

const PDF_PATHS={
  "Endangered Plant and Animal Species of Maharashtra: A Study on Biodiversity Loss":"published/JD Biology Project.pdf",
  "Avian Migration: A Study of Migratory Birds Visiting Various Habitats in Maharashtra":"published/Rudra Bio project.pdf",
  "Urban Ecology and Arboriculture: A Study of Different Avenue Trees and Their Importance":"published/Mayur Mali Biology Project.pdf"
};

const demo=[
{id:"jd-static",title:"Endangered Plant and Animal Species of Maharashtra: A Study on Biodiversity Loss",class_level:"12",category:"Biodiversity & Conservation",chapter:"Biodiversity and Conservation",practical_no:"Project Topic 3",description:"A Maharashtra-focused study of endangered flora and fauna, classification, causes of biodiversity loss, analysis and conservation strategies.",tags:["Biodiversity","Conservation","Maharashtra"]},
{id:"rudra-static",title:"Avian Migration: A Study of Migratory Birds Visiting Various Habitats in Maharashtra",class_level:"12",category:"Ecology & Environment",chapter:"Ecology and Biodiversity",practical_no:"Project Topic 14",description:"A study of migratory birds visiting Maharashtra, including taxonomy, habitats, ecological importance, threats and conservation strategies.",tags:["Migratory Birds","Ecology","Wetlands","Maharashtra"]},
{id:"mayur-static",title:"Urban Ecology and Arboriculture: A Study of Different Avenue Trees and Their Importance",class_level:"12",category:"Ecology & Environment",chapter:"Ecology and Arboriculture",practical_no:"Project Topic 12",description:"A study of common avenue trees, their taxonomy, ecological value, medicinal and economic importance, and their role in urban environments.",tags:["Avenue Trees","Ecology","Arboriculture","Maharashtra"]}
];

function esc(s=""){return String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#039;"}[c]))}
function pdfPath(p){return p.pdf_path||PDF_PATHS[p.title]||""}
function normalize(p){return {...p,pdf_path:pdfPath(p),pdf_url:""}}
function poster(p){return '<div class="poster"><b>'+esc(p.title)+'</b></div>'}
function card(p){const hasPdf=Boolean(pdfPath(p));return '<article class="card">'+poster(p)+'<div class="card-body"><span class="pill">Class '+esc(p.class_level)+'</span><h3>'+esc(p.title)+'</h3><p>'+esc(p.description||"Biology project resource.")+'</p><div class="meta"><span>'+esc(p.chapter||"Biology")+'</span><span>'+esc(p.practical_no||"Project")+'</span></div><div class="card-actions"><button class="btn ghost" data-id="'+esc(p.id)+'">'+(hasPdf?"Preview":"Preview")+'</button>'+(hasPdf?'<button class="btn primary" data-open="'+esc(p.id)+'">Open PDF ↗</button>':'<span class="btn soft">PDF pending</span>')+'</div></div></article>'}

async function load(){
  if(!sb){projects=demo.map(normalize);$("#status").textContent="Published library";render();return}
  try{
    const r=await sb.from("projects").select("*").order("created_at",{ascending:false});
    if(r.error)throw r.error;
    const remote=(r.data||[]).map(normalize);
    const keys=new Set(remote.map(p=>p.title));
    projects=[...remote,...demo.filter(p=>!keys.has(p.title)).map(normalize)];
    $("#status").textContent=remote.length?"Live library":"Published library";
  }catch(e){
    projects=demo.map(normalize);
    $("#status").textContent="Published library";
  }
  render();
}

function render(){
  const q=$("#search").value.toLowerCase().trim(),cl=$("#class").value,cat=$("#category").value;
  const list=projects.filter(p=>(!q||[p.title,p.chapter,p.category,p.description,...(p.tags||[])].join(" ").toLowerCase().includes(q))&&(cl==="all"||String(p.class_level)===cl)&&(cat==="all"||p.category===cat));
  $("#grid").innerHTML=list.length?list.map(card).join(""):'<div class="empty">No projects match your search.</div>';
  $("#total").textContent=projects.length;$("#c11").textContent=projects.filter(p=>String(p.class_level)==="11").length;$("#c12").textContent=projects.filter(p=>String(p.class_level)==="12").length;$("#cats").textContent=new Set(projects.map(p=>p.category).filter(Boolean)).size;$("#heroCount").textContent=projects.length;
  const cats=[...new Set(projects.map(p=>p.category).filter(Boolean))].sort(),selected=$("#category").value;
  $("#category").innerHTML='<option value="all">All categories</option>'+cats.map(x=>'<option value="'+esc(x)+'">'+esc(x)+'</option>').join("");$("#category").value=cats.includes(selected)?selected:"all";
  $("#grid").querySelectorAll("[data-id]").forEach(b=>b.onclick=()=>openProject(b.dataset.id));$("#grid").querySelectorAll("[data-open]").forEach(b=>b.onclick=()=>openProject(b.dataset.open));
}

async function signedPdfUrl(p){
  if(!session?.user)throw new Error("SIGN_IN_REQUIRED");
  const path=pdfPath(p);
  if(!path)throw new Error("PDF is not attached yet.");
  const r=await sb.storage.from("project-pdfs").createSignedUrl(path,600);
  if(r.error)throw r.error;
  return r.data.signedUrl;
}
function showPdfGate(message="Sign in with Google to view or download this original PDF."){
  const gate=$("#pdfGate");
  $("#previewWrap").classList.add("hidden");
  $("#viewPdf").classList.add("hidden");
  $("#downloadPdf").classList.add("hidden");
  $("#noPdf").classList.add("hidden");
  gate.textContent="";
  const strong=document.createElement("b");strong.textContent="Sign-in required";
  const br=document.createElement("br");
  const span=document.createElement("span");span.textContent=message;
  const div=document.createElement("div");div.style.marginTop="12px";
  const btn=document.createElement("button");btn.className="btn primary";btn.textContent="Continue with Google";btn.onclick=login;
  div.appendChild(btn);gate.append(strong,br,span,div);gate.classList.remove("hidden");
}
async function openProject(id){
  const p=projects.find(x=>x.id===id);if(!p)return;
  $("#modalPoster").innerHTML=poster(p);$("#modalClass").textContent="Class "+(p.class_level||"");$("#modalTitle").textContent=p.title;$("#modalDesc").textContent=p.description||"";
  $("#modalMeta").innerHTML=[p.category,p.chapter,p.practical_no].filter(Boolean).map(x=>"<span>"+esc(x)+"</span>").join("");
  const v=$("#viewPdf"),d=$("#downloadPdf"),wrap=$("#previewWrap"),frame=$("#pdfPreview"),none=$("#noPdf"),gate=$("#pdfGate");
  wrap.classList.add("hidden");frame.removeAttribute("src");v.classList.add("hidden");d.classList.add("hidden");none.classList.add("hidden");gate.classList.add("hidden");
  $("#modal").classList.remove("hidden");
  const path=pdfPath(p);
  if(!path){none.classList.remove("hidden");return}
  if(!session?.user){showPdfGate();return}
  try{
    const url=await signedPdfUrl(p);
    frame.src=url;wrap.classList.remove("hidden");v.href=url;v.classList.remove("hidden");d.href=url;d.setAttribute("download",p.title.replace(/[^a-z0-9]+/gi,"-")+".pdf");d.classList.remove("hidden");
  }catch(e){
    showPdfGate(e.message||"The secure PDF could not be opened.");
  }
}
function closeModal(){$("#modal").classList.add("hidden");$("#pdfPreview").removeAttribute("src")}

async function login(){
  if(!sb){showAuthNote("Authentication is not configured yet.");return}
  $("#status").textContent="Opening Google sign-in…";
  const r=await sb.auth.signInWithOAuth({provider:"google",options:{redirectTo:location.href}});
  if(r.error){$("#status").textContent="Ready";showAuthNote(r.error.message)}
}
async function logout(){if(sb)await sb.auth.signOut()}
async function refreshAuth(){
  if(!sb)return;
  const r=await sb.auth.getSession();session=r.data.session;await account();
  if(!authListener)authListener=sb.auth.onAuthStateChange(async(_event,s)=>{session=s;await account()}).data.subscription;
}
async function account(){
  if(!session?.user){isAdmin=false;$("#loginBtn").classList.remove("hidden");$("#logoutBtn").classList.add("hidden");$("#adminBtn").classList.add("hidden");closeModal();return}
  $("#loginBtn").classList.add("hidden");$("#logoutBtn").classList.remove("hidden");
  const r=await sb.rpc("is_admin");isAdmin=!r.error&&Boolean(r.data);$("#adminBtn").classList.toggle("hidden",!isAdmin);
  if(isAdmin)$("#adminMsg").textContent="You are signed in as an approved administrator.";
}
function showAuthNote(msg){$("#authNote").textContent=msg;$("#authNote").classList.remove("hidden")}
function openAdmin(){if(!isAdmin)return;$("#adminPanel").classList.remove("hidden");$("#projectForm").classList.remove("hidden");loadAdmin()}
function closeAdmin(){$("#adminPanel").classList.add("hidden")}
async function loadAdmin(){
  const r=await sb.from("projects").select("*").order("created_at",{ascending:false});
  $("#adminList").innerHTML=(r.data||[]).map(p=>'<div class="admin-row"><div><b>'+esc(p.title)+'</b><small style="display:block;color:#657a71">Class '+esc(p.class_level)+' · '+esc(p.category||"")+'</small></div><button class="btn ghost mini" data-del="'+esc(p.id)+'">Delete</button></div>').join("");
  $("#adminList").querySelectorAll("[data-del]").forEach(b=>b.onclick=()=>delProject(b.dataset.del));
}
async function upload(bucket,path,file){
  const r=await sb.storage.from(bucket).upload(path,file,{upsert:true,contentType:file.type||"application/pdf",cacheControl:"3600"});
  if(r.error)throw r.error;
}
$("#projectForm").onsubmit=async e=>{
  e.preventDefault();
  try{
    const id=crypto.randomUUID(),pdf=$("#fPdf").files[0];if(!pdf)throw Error("Choose a PDF.");if(pdf.type&&pdf.type!=="application/pdf")throw Error("Only PDF files are supported.");
    const pdfPath=id+"/"+Date.now()+"-"+pdf.name.replace(/[^a-z0-9.-]/gi,"-");await upload("project-pdfs",pdfPath,pdf);
    const data={id,title:$("#fTitle").value.trim(),class_level:$("#fClass").value,practical_no:$("#fTopic").value?"Project Topic "+$("#fTopic").value:"",category:$("#fCategory").value.trim()||"Biology",chapter:$("#fChapter").value.trim(),description:$("#fDesc").value.trim(),tags:$("#fTags").value.split(",").map(x=>x.trim()).filter(Boolean),pdf_path:pdfPath,pdf_url:null,updated_at:new Date().toISOString()};
    const r=await sb.from("projects").insert(data);if(r.error)throw r.error;$("#projectForm").reset();await load();await loadAdmin();alert("Project published.");
  }catch(e){alert(e.message||"Publish failed.")}
}
async function delProject(id){if(!confirm("Delete this project?"))return;const r=await sb.from("projects").delete().eq("id",id);if(r.error)return alert(r.error.message);await load();await loadAdmin()}
async function secureExistingPdfs(){
  if(!session?.user){showAuthNote("Sign in with Google to secure the published PDFs.");return}
  if(!isAdmin)return;
  const b=$("#secureExisting");if(b)b.disabled=true;
  try{
    const r=await sb.functions.invoke("secure-project-pdfs",{body:{}});
    if(r.error)throw r.error;
    await load();await loadAdmin();
    alert("The existing project PDFs are now protected. Signed-in users can view and download them.");
  }catch(e){
    alert(e.message||"Could not secure the existing PDFs.");
  }finally{
    if(b)b.disabled=false;
  }
}
$("#search").oninput=render;$("#class").onchange=render;$("#category").onchange=render;$("#loginBtn").onclick=login;$("#logoutBtn").onclick=logout;$("#adminBtn").onclick=openAdmin;$("#close").onclick=closeModal;$("#adminClose").onclick=closeAdmin;$("#secureExisting").onclick=secureExistingPdfs;
window.addEventListener("keydown",e=>{if(e.key==="Escape"){closeModal();closeAdmin()}});
load();refreshAuth();