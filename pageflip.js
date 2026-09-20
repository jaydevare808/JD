(()=>{const start=()=>{const box=document.getElementById("pdfCanvasWrap"),canvas=document.getElementById("pdfCanvas"),prev=document.getElementById("pdfPrev"),next=document.getElementById("pdfNext");if(!box||!canvas||!prev||!next)return;canvas.classList.add("pdf-canvas");const prevAction=prev.onclick,nextAction=next.onclick;let busy=false,lastTouch=0,audio=null,audioReady=false;
const getAudio=()=>{try{const AC=window.AudioContext||window.webkitAudioContext;if(!AC)return null;if(!audio)audio=new AC();if(audio.state==="suspended")audio.resume();audioReady=true;return audio}catch(e){return null}};
const unlockAudio=()=>{const ctx=getAudio();if(ctx&&ctx.state==="suspended")ctx.resume().catch(()=>{});};
const paperSound=()=>{const ctx=getAudio();if(!ctx)return;const now=ctx.currentTime;try{
const len=Math.floor(ctx.sampleRate*.28),buffer=ctx.createBuffer(1,len,ctx.sampleRate),data=buffer.getChannelData(0);
for(let i=0;i<len;i++){const t=i/len;const envelope=Math.pow(1-t,1.4);data[i]=(Math.random()*2-1)*envelope*.9;}
const src=ctx.createBufferSource();src.buffer=buffer;
const filter=ctx.createBiquadFilter();filter.type="bandpass";filter.Q.value=.65;filter.frequency.setValueAtTime(1500,now);filter.frequency.exponentialRampToValueAtTime(700,now+.24);
const gain=ctx.createGain();gain.gain.setValueAtTime(.0001,now);gain.gain.exponentialRampToValueAtTime(.42,now+.012);gain.gain.exponentialRampToValueAtTime(.0001,now+.27);
src.connect(filter).connect(gain).connect(ctx.destination);src.start(now);src.stop(now+.28);
const osc=ctx.createOscillator(),og=ctx.createGain();osc.type="sine";osc.frequency.setValueAtTime(260,now);osc.frequency.exponentialRampToValueAtTime(95,now+.24);og.gain.setValueAtTime(.0001,now);og.gain.exponentialRampToValueAtTime(.055,now+.02);og.gain.exponentialRampToValueAtTime(.0001,now+.24);osc.connect(og).connect(ctx.destination);osc.start(now);osc.stop(now+.25);
}catch(e){}};
const turn=(dir)=>{const action=dir<0?prevAction:nextAction,button=dir<0?prev:next;if(busy||button.disabled||typeof action!=="function")return false;busy=true;unlockAudio();paperSound();canvas.classList.remove("flip-next","flip-prev");void canvas.offsetWidth;canvas.classList.add(dir<0?"flip-prev":"flip-next");setTimeout(()=>{try{action.call(button)}catch(e){}},330);setTimeout(()=>{canvas.classList.remove("flip-next","flip-prev");busy=false},760);return true};
box.addEventListener("pointerdown",unlockAudio,{passive:true});
box.addEventListener("click",e=>{if(Date.now()-lastTouch<600)return;if(e.target.closest(".pdf-toolbar"))return;const r=box.getBoundingClientRect();turn(e.clientX<r.left+r.width/2?-1:1)});
let touchX=0;box.addEventListener("touchstart",e=>{touchX=e.changedTouches[0].clientX;lastTouch=Date.now();unlockAudio()},{passive:true});box.addEventListener("touchend",e=>{const dx=e.changedTouches[0].clientX-touchX;lastTouch=Date.now();if(Math.abs(dx)>50)turn(dx<0?1:-1)},{passive:true});
window.addEventListener("keydown",e=>{const modal=document.getElementById("modal");if(!modal||modal.classList.contains("hidden"))return;if(e.key==="ArrowRight")turn(1);else if(e.key==="ArrowLeft")turn(-1)});
};if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",start,{once:true});else start()})();