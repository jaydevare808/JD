(()=>{
  const start=()=>{
    const box=document.getElementById("pdfCanvasWrap");
    const canvas=document.getElementById("pdfCanvas");
    const prev=document.getElementById("pdfPrev");
    const next=document.getElementById("pdfNext");
    if(!box||!canvas||!prev||!next)return;

    const prevAction=prev.onclick;
    const nextAction=next.onclick;
    let busy=false,lastTouch=0,flipAudio=null;

    const style=document.createElement("style");
    style.textContent=\`
      .pdf-page-old{
        position:absolute!important;
        z-index:20!important;
        pointer-events:none!important;
        display:block;
        background:#fff;
        max-width:none!important;
        transform-style:preserve-3d;
        backface-visibility:hidden;
        will-change:transform,filter,box-shadow;
        transform-origin:right center;
      }
      .pdf-page-old.old-prev{transform-origin:left center}
      .pdf-page-curl{
        position:absolute!important;
        z-index:21!important;
        pointer-events:none!important;
        width:22%!important;
        height:100%!important;
        top:0!important;
        opacity:0;
        will-change:transform,opacity;
        background:linear-gradient(90deg,transparent 0%,rgba(255,255,255,.24) 30%,rgba(255,255,255,.56) 48%,rgba(0,0,0,.10) 68%,transparent 100%);
        filter:blur(.25px);
      }
      .pdf-page-curl.next{right:0;transform-origin:right center}
      .pdf-page-curl.prev{left:0;transform-origin:left center}
      .pdf-page-old.old-next{animation:bioPageNext .82s cubic-bezier(.18,.76,.20,1) forwards}
      .pdf-page-old.old-prev{animation:bioPagePrev .82s cubic-bezier(.18,.76,.20,1) forwards}
      .pdf-page-curl.next{animation:bioCurlNext .82s cubic-bezier(.18,.76,.20,1) forwards}
      .pdf-page-curl.prev{animation:bioCurlPrev .82s cubic-bezier(.18,.76,.20,1) forwards}

      @keyframes bioPageNext{
        0%{transform:perspective(1900px) rotateY(0deg) translateX(0) scale(1);filter:brightness(1);box-shadow:0 9px 25px rgba(0,0,0,.16)}
        18%{transform:perspective(1900px) rotateY(-18deg) translateX(-.3%) scale(.999);filter:brightness(.99);box-shadow:10px 10px 30px rgba(0,0,0,.20)}
        52%{transform:perspective(1900px) rotateY(-92deg) translateX(-1.1%) scale(.996);filter:brightness(.98);box-shadow:18px 10px 32px rgba(0,0,0,.17)}
        78%{transform:perspective(1900px) rotateY(-145deg) translateX(-1.8%) scale(.992);filter:brightness(.97);box-shadow:10px 7px 26px rgba(0,0,0,.10)}
        100%{transform:perspective(1900px) rotateY(-176deg) translateX(-2%) scale(.989);filter:brightness(.96);box-shadow:0 0 0 rgba(0,0,0,0)}
      }
      @keyframes bioPagePrev{
        0%{transform:perspective(1900px) rotateY(0deg) translateX(0) scale(1);filter:brightness(1);box-shadow:0 9px 25px rgba(0,0,0,.16)}
        18%{transform:perspective(1900px) rotateY(18deg) translateX(.3%) scale(.999);filter:brightness(.99);box-shadow:-10px 10px 30px rgba(0,0,0,.20)}
        52%{transform:perspective(1900px) rotateY(92deg) translateX(1.1%) scale(.996);filter:brightness(.98);box-shadow:-18px 10px 32px rgba(0,0,0,.17)}
        78%{transform:perspective(1900px) rotateY(145deg) translateX(1.8%) scale(.992);filter:brightness(.97);box-shadow:-10px 7px 26px rgba(0,0,0,.10)}
        100%{transform:perspective(1900px) rotateY(176deg) translateX(2%) scale(.989);filter:brightness(.96);box-shadow:0 0 0 rgba(0,0,0,0)}
      }
      @keyframes bioCurlNext{
        0%{opacity:0;transform:translateX(0) scaleX(1)}
        14%{opacity:.12}
        42%{opacity:.42;transform:translateX(-35%) scaleX(.82)}
        76%{opacity:.16;transform:translateX(-88%) scaleX(.58)}
        100%{opacity:0;transform:translateX(-115%) scaleX(.48)}
      }
      @keyframes bioCurlPrev{
        0%{opacity:0;transform:translateX(0) scaleX(1)}
        14%{opacity:.12}
        42%{opacity:.42;transform:translateX(35%) scaleX(.82)}
        76%{opacity:.16;transform:translateX(88%) scaleX(.58)}
        100%{opacity:0;transform:translateX(115%) scaleX(.48)}
      }
      @media(prefers-reduced-motion:reduce){
        .pdf-page-old.old-next,.pdf-page-old.old-prev,.pdf-page-curl.next,.pdf-page-curl.prev{animation-duration:.18s}
      }
    \`;
    document.head.appendChild(style);

    flipAudio=new Audio("./audio/page-flip-smooth.mp3");
    flipAudio.preload="auto";
    flipAudio.volume=.32;

    const playFlipSound=()=>{
      if(!flipAudio)return;
      try{
        flipAudio.pause();
        flipAudio.currentTime=0;
        const p=flipAudio.play();
        if(p&&typeof p.catch==="function")p.catch(()=>{});
      }catch(e){}
    };

    const animate=dir=>{
      const action=dir<0?prevAction:nextAction;
      const button=dir<0?prev:next;
      if(busy||button.disabled||typeof action!=="function")return false;
      busy=true;

      const old=canvas.cloneNode(true);
      old.className="pdf-page-old "+(dir>0?"old-next":"old-prev");
      old.removeAttribute("id");
      old.setAttribute("aria-hidden","true");

      const curl=document.createElement("div");
      curl.className="pdf-page-curl "+(dir>0?"next":"prev");
      curl.setAttribute("aria-hidden","true");

      const cs=getComputedStyle(canvas);
      const r=canvas.getBoundingClientRect();
      const br=box.getBoundingClientRect();
      old.width=canvas.width;
      old.height=canvas.height;
      old.style.width=cs.width;
      old.style.height=cs.height;
      old.style.left=(r.left-br.left+box.scrollLeft)+"px";
      old.style.top=(r.top-br.top+box.scrollTop)+"px";
      curl.style.left=old.style.left;
      curl.style.top=old.style.top;
      curl.style.height=cs.height;
      curl.style.width=Math.max(80,Math.round(parseFloat(cs.width||"800")*.22))+"px";

      box.appendChild(old);
      box.appendChild(curl);

      try{action.call(button)}catch(e){}
      requestAnimationFrame(()=>requestAnimationFrame(playFlipSound));

      const cleanup=()=>{
        old.remove();
        curl.remove();
        busy=false;
      };
      old.addEventListener("animationend",cleanup,{once:true});
      setTimeout(cleanup,980);
      return true;
    };

    box.addEventListener("click",e=>{
      if(Date.now()-lastTouch<650||e.target.closest(".pdf-toolbar"))return;
      const r=box.getBoundingClientRect();
      animate(e.clientX<r.left+r.width/2?-1:1);
    });

    let touchX=0;
    box.addEventListener("touchstart",e=>{
      touchX=e.changedTouches[0].clientX;
      lastTouch=Date.now();
    },{passive:true});

    box.addEventListener("touchend",e=>{
      const dx=e.changedTouches[0].clientX-touchX;
      lastTouch=Date.now();
      if(Math.abs(dx)>48)animate(dx<0?1:-1);
    },{passive:true});

    window.addEventListener("keydown",e=>{
      const modal=document.getElementById("modal");
      if(!modal||modal.classList.contains("hidden"))return;
      if(e.key==="ArrowRight")animate(1);
      else if(e.key==="ArrowLeft")animate(-1);
    });
  };

  if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",start,{once:true});
  else start();
})();