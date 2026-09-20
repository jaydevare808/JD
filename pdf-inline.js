(()=>{
  const ready=()=>{
    const wrap=document.getElementById("previewWrap");
    if(!wrap)return;
    let frame=document.getElementById("pdfPreview");
    if(!frame){
      frame=document.createElement("iframe");
      frame.id="pdfPreview";
      frame.title="PDF data bridge";
      frame.style.display="none";
      frame.setAttribute("aria-hidden","true");
      wrap.appendChild(frame);
    }
    const canvas=document.getElementById("pdfCanvas");
    const canvasWrap=document.getElementById("pdfCanvasWrap");
    const loading=document.getElementById("pdfLoading");
    const error=document.getElementById("pdfError");
    const prev=document.getElementById("pdfPrev");
    const next=document.getElementById("pdfNext");
    const zoomOut=document.getElementById("pdfZoomOut");
    const zoomIn=document.getElementById("pdfZoomIn");
    const pageInfo=document.getElementById("pdfPageInfo");
    const zoom=document.getElementById("pdfZoom");
    if(!canvas||!canvasWrap||!window.pdfjsLib)return;

    let doc=null,pageNo=1,pages=0,scale=1.1,lastSrc="";

    const clear=()=>{
      if(doc){try{doc.destroy()}catch(e){}}
      doc=null;pageNo=1;pages=0;lastSrc="";
      canvas.width=1;canvas.height=1;
      if(pageInfo)pageInfo.textContent="Page — / —";
      if(zoom)zoom.textContent=Math.round(scale*100)+"%";
      if(loading)loading.classList.add("hidden");
      if(error)error.classList.add("hidden");
    };

    const render=async n=>{
      if(!doc)return;
      try{
        const page=await doc.getPage(n);
        const base=page.getViewport({scale});
        const available=Math.max(320,(canvasWrap.clientWidth||900)-24);
        const fit=Math.min(scale,available/base.width);
        const viewport=page.getViewport({scale:scale<=1.1?fit:scale});
        const ratio=window.devicePixelRatio||1;
        canvas.width=Math.ceil(viewport.width*ratio);
        canvas.height=Math.ceil(viewport.height*ratio);
        canvas.style.width=Math.ceil(viewport.width)+"px";
        canvas.style.height=Math.ceil(viewport.height)+"px";
        const ctx=canvas.getContext("2d");
        ctx.setTransform(ratio,0,0,ratio,0,0);
        await page.render({canvasContext:ctx,viewport}).promise;
        pageNo=n;
        if(loading)loading.classList.add("hidden");
        if(error)error.classList.add("hidden");
        if(pageInfo)pageInfo.textContent="Page "+n+" / "+pages;
        if(prev)prev.disabled=n<=1;
        if(next)next.disabled=n>=pages;
        if(zoom)zoom.textContent=Math.round(scale*100)+"%";
      }catch(e){
        if(loading)loading.classList.add("hidden");
        if(error){error.textContent="Unable to render this PDF preview.";error.classList.remove("hidden")}
      }
    };

    const load=async src=>{
      if(!src||src===lastSrc)return;
      lastSrc=src;
      clear();
      lastSrc=src;
      if(loading)loading.classList.remove("hidden");
      try{
        pdfjsLib.GlobalWorkerOptions.workerSrc="https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js";
        doc=await pdfjsLib.getDocument(src).promise;
        pages=doc.numPages;
        await render(1);
      }catch(e){
        if(loading)loading.classList.add("hidden");
        if(error){error.textContent="Unable to load the PDF preview.";error.classList.remove("hidden")}
      }
    };

    prev&&(prev.onclick=()=>{if(pageNo>1)render(pageNo-1)});
    next&&(next.onclick=()=>{if(pageNo<pages)render(pageNo+1)});
    zoomOut&&(zoomOut.onclick=()=>{scale=Math.max(.8,scale-.15);render(pageNo)});
    zoomIn&&(zoomIn.onclick=()=>{scale=Math.min(2,scale+.15);render(pageNo)});

    new MutationObserver(()=>{
      const src=frame.getAttribute("src")||"";
      if(src)load(src); else if(lastSrc)clear();
    }).observe(frame,{attributes:true,attributeFilter:["src"]});

    frame.addEventListener("load",()=>{
      const src=frame.getAttribute("src")||"";
      if(src)load(src);
    });
  };
  if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",ready,{once:true});
  else ready();
})();