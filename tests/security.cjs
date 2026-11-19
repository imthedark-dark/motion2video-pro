const {chromium}=require('playwright-core');
const fs=require('fs'),path=require('path'),assert=require('assert'),cp=require('child_process'),https=require('https');
(async()=>{
 const root=path.resolve(__dirname,'..'),out=path.join(root,'build/security-test');fs.mkdirSync(out,{recursive:true});
 // Ephemeral test-only TLS key lives under ignored build/, never in source.
 cp.execFileSync('openssl',['req','-x509','-newkey','rsa:2048','-nodes','-keyout',out+'/key.pem','-out',out+'/cert.pem','-subj','/CN=localhost','-days','1','-addext','subjectAltName=IP:127.0.0.1'],{stdio:'ignore'});
 let hits=[];const server=https.createServer({key:fs.readFileSync(out+'/key.pem'),cert:fs.readFileSync(out+'/cert.pem')},(q,r)=>{const p=q.url;hits.push(p);r.setHeader('Access-Control-Allow-Origin','*');r.setHeader('Access-Control-Allow-Headers','*');r.setHeader('Access-Control-Allow-Private-Network','true');r.setHeader('Content-Type',p.endsWith('.js')?'application/javascript':p.endsWith('.css')?'text/css':p.endsWith('.svg')?'image/svg+xml':'text/plain');r.end(p.endsWith('.js')?'window.externalScript=true':p.endsWith('.css')?'body{color:white}':p.endsWith('.svg')?'<svg xmlns="http://www.w3.org/2000/svg"/>':'ok')});
 server.on('upgrade',(q,s)=>{hits.push(q.url);s.destroy()});await new Promise(r=>server.listen(0,'127.0.0.1',r));const endpoint='https://127.0.0.1:'+server.address().port;
 let b;
 try{
  const probe=fs.readFileSync(root+'/tests/security/network-probe.html','utf8').replaceAll('https://security-test.invalid',endpoint).replaceAll('wss://security-test.invalid',endpoint.replace('https:','wss:'));fs.writeFileSync(out+'/probe.html',probe);
  cp.execFileSync('java',['-m','jdk.compiler/com.sun.tools.javac.Main','-d',out,root+'/app/src/main/java/dev/motion2video/app/ImportedContent.java',root+'/tests/java/ImportedContentTest.java'],{stdio:'inherit'});
  cp.execFileSync('java',['-cp',out,'ImportedContentTest',out+'/probe.html',out],{stdio:'inherit'});
  b=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||chromium.executablePath(),headless:true,args:['--no-sandbox','--disable-dev-shm-usage','--no-proxy-server']});
  for(const online of [false,true]){
   hits=[];const context=await b.newContext({ignoreHTTPSErrors:true}),p=await context.newPage();
   await p.setContent('<meta http-equiv="Content-Security-Policy" content="frame-src about: blob:"><iframe sandbox="allow-scripts"></iframe>');
   await p.locator('iframe').evaluate((el,html)=>el.srcdoc=html,fs.readFileSync(out+(online?'/online.html':'/offline.html'),'utf8'));const child=p.frames().find(f=>f.parentFrame());
   await child.waitForFunction(()=>window.probe?.done);if(online)await child.waitForFunction(()=>window.externalScript===true);await p.waitForTimeout(250);const result=await child.evaluate(()=>window.probe);
   assert.equal(await child.evaluate(()=>document.compatMode),'CSS1Compat','doctype changed');
   await child.evaluate(url=>{location.href=url},endpoint+'/navigation');await p.waitForTimeout(150);
   assert(!hits.includes('/navigation'),'imported content navigated outside sandbox');assert.equal(result.javaBridge,false);assert(!hits.includes('/frame'));assert(!hits.includes('/worker.js'));
   if(!online){assert.deepEqual(hits,[],'offline content reached real TLS server');assert(result.violations.some(v=>v.directive==='connect-src'&&v.blocked.startsWith('wss:')));assert(result.violations.some(v=>v.directive==='connect-src'&&v.blocked.includes('/beacon')));assert.equal(result.fetch,false);assert.equal(result.xhr,false);assert.equal(result.rtc,'undefined')}
   else{assert(result.fetch&&result.xhr);for(const resource of ['/fetch','/xhr','/script.js','/style.css','/image.svg'])assert(hits.includes(resource),'opt-in must allow '+resource)}
   await context.close();
  }
  console.log('PASS: production Java CSP rejects offline network probes (zero TLS hits), frame/worker/navigation and WebRTC escape paths; explicit HTTPS opt-in works.');
 }finally{if(b)await b.close();server.closeAllConnections();await new Promise(r=>server.close(r));fs.rmSync(out+'/key.pem',{force:true});}
})().catch(e=>{console.error(e);process.exit(1)});
