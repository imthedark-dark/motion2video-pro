/* Deterministic animation clock. CSS fallback supports WebViews without Document.getAnimations. */
(function(){'use strict';
var cssRecords=null,clockStyle=null,framePort=null;
window.addEventListener('message',function(e){if((e.source===null||e.source===window)&&e.data==='motion-clock-port-v1'&&e.ports&&e.ports.length===1&&!framePort){framePort=e.ports[0];framePort.start();framePort.postMessage('attached');}});
function seconds(v){return parseFloat(v)*(String(v).trim().slice(-2)==='ms'?.001:1)||0;}
function scan(){if(cssRecords)return cssRecords;cssRecords=[];var id=0;
 Array.prototype.forEach.call(document.querySelectorAll('*'),function(el){
  if(el===clockStyle)return;
  ['', '::before','::after'].forEach(function(pseudo){var s=getComputedStyle(el,pseudo||null);if(!s.animationName||s.animationName==='none')return;
   var key=el.getAttribute('data-m2v-clock');if(!key){key=String(++id);el.setAttribute('data-m2v-clock',key);}
   cssRecords.push({el:el,selector:'[data-m2v-clock="'+key+'"]'+pseudo,names:s.animationName,delays:s.animationDelay.split(',').map(seconds),durations:s.animationDuration.split(',').map(seconds),counts:s.animationIterationCount.split(',').map(function(n){return n.trim()==='infinite'?Infinity:parseFloat(n)})});
  });
 });return cssRecords;
}
function fallback(time){var records=scan();if(!records.length)return;if(!clockStyle){clockStyle=document.createElement('style');clockStyle.id='m2v-clock-style';document.head.appendChild(clockStyle);}
 clockStyle.textContent=records.map(function(r){return r.selector+'{animation-name:none!important}'}).join('');
 void document.documentElement.offsetWidth;
 clockStyle.textContent=records.map(function(r){var count=r.names.split(',').length,delays=[];for(var i=0;i<count;i++)delays.push((r.delays[i%r.delays.length]-time)+'s');return r.selector+'{animation-name:'+r.names+'!important;animation-delay:'+delays.join(',')+'!important;animation-play-state:paused!important}'}).join('');
 void document.documentElement.offsetWidth;
}
window.__m2vDuration=function(){var timeline=window.motionTimeline||(window.gsap&&gsap.globalTimeline),d=timeline?timeline.duration():0;
 if(document.getAnimations)document.getAnimations().forEach(function(a){var e=a.effect.getComputedTiming().endTime/1000;if(isFinite(e))d=Math.max(d,e)});
 else scan().forEach(function(r){r.names.split(',').forEach(function(_,i){var e=r.delays[i%r.delays.length]+r.durations[i%r.durations.length]*r.counts[i%r.counts.length];if(isFinite(e))d=Math.max(d,e)});});
 return isFinite(d)&&d>0&&d<=300?Math.ceil(d*10)/10:0;
};
window.__m2vSeek=function(time,token){window.__m2vFrameReady=null;
 if(window.gsap){var t=window.motionTimeline||gsap.globalTimeline;t.pause();t.time(time,false);}
 if(document.getAnimations)document.getAnimations().forEach(function(a){a.pause();a.currentTime=time*1000});else fallback(time);
 if(window.lottie&&lottie.getRegisteredAnimations)lottie.getRegisteredAnimations().forEach(function(a){a.pause();a.goToAndStop(time*a.frameRate,true)});
 if(typeof window.renderFrame==='function')window.renderFrame(time);
 requestAnimationFrame(function(){requestAnimationFrame(function(){window.__m2vFrameReady=token;if(framePort)framePort.postMessage(String(token));});});
 return true;
};
})();
