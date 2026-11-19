const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {execFileSync: run} = require('node:child_process');
const {chromium} = require('playwright-core');

const root = path.resolve(__dirname, '..');
const javaPath = 'app/src/main/java/dev/motion2video/app/';
const assetPath = 'app/src/main/assets';
const fixture = fs.readFileSync(path.join(__dirname, 'fixtures/motion-video.html'), 'utf8');
const googleURL = fixture.match(/https:\/\/fonts\.googleapis\.com\/[^']+/)[0];
const refs = ['v1.4.1', ...[1,2,3,4,5].map(i => 'security/phase-' + i), 'feature/phase-6', 'worktree'];

function get(ref, file) {
  return ref === 'worktree' ? fs.readFileSync(path.join(root, file), 'utf8') :
    run('git', ['show', ref + ':' + file], {cwd: root, encoding: 'utf8'});
}

function makeStage(ref, tmp, htmlSource = fixture, seed = false) {
  const input = path.join(tmp,'fixture.html');
  fs.writeFileSync(input,htmlSource);
  const source = get(ref, javaPath + 'StageAssets.java');
  const oldResponse = source.includes('Context c,Uri uri)');
  const fontCache = source.includes("FontCache.Scope");
  const fontNotice = source.includes('IntConsumer fontNotice');
  const previewJava = get(ref, javaPath + 'MainActivity.java');
  const prepared = previewJava.includes('ImportedContent.prepare(');
  const assets = ref === 'worktree' ? path.join(root, assetPath) : path.join(tmp, assetPath);
  if (ref !== 'worktree') {
    run('tar', ['-x', '-C', tmp], {input: run('git', ['archive',ref,assetPath], {cwd:root,maxBuffer:16*1024*1024})});
  }
  const contextSource = path.join(__dirname, 'host-android/android');
  fs.writeFileSync(path.join(tmp, 'StageAssets.java'), source);
  let imports = '';
  if (!oldResponse) {
    const content = get(ref, javaPath + 'ImportedContent.java');
    fs.writeFileSync(path.join(tmp, 'ImportedContent.java'), content);
    imports = 'ImportedContent.java';
  }
  // Execute the real production asset policy with minimal Android API stand-ins.
  // For v1.4.1 this StageAssets is the service policy. MainActivity's duplicate
  // preview policy returns the same result for the exact Google URL and local fonts.
  fs.writeFileSync(path.join(tmp, 'HostStage.java'), `
package dev.motion2video.app;
import java.io.*;
import java.nio.file.*;
import java.util.Base64;
import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceResponse;
public final class HostStage {
 public static void main(String[] a)throws Exception{
  Path root=Path.of(a[0]);
  Context c=new Context(root);
  ${fontCache?`
  FontCache cache=new FontCache(Path.of("host-cache"));
  if(a[1].equals("seed")){
   byte[] font=Files.readAllBytes(root.resolve("fonts/anton-Anton-Regular.ttf"));
   String css="@font-face{font-family:Cairo;src:url(https://fonts.gstatic.com/s/cairo/test.ttf) format('truetype');}";
   FontDownload downloader=new FontDownload((url,limit,control)->"fonts.googleapis.com".equals(url.getHost())
    ?new FontDownload.Reply(200,"text/css",css.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    :new FontDownload.Reply(200,"font/ttf",font));
   FontDownload.Control control=new FontDownload.Control();
   cache.save(downloader.download("Cairo",control,bytes->{}),control);
   return;
  }
  FontDiscovery needed=FontDiscovery.scan(Files.readString(Path.of("fixture.html")));
  java.util.Set<String> families=new java.util.HashSet<>(needed.google);
  families.addAll(needed.declared);
  FontCache.Scope scope=cache.scope(families,false);
  `:''}
  if(a[1].equals("document")){
   String html=Files.readString(Path.of(a[2]));
   String clock=StageAssets.text(c,"motion-clock.js"),fonts=StageAssets.text(c,"fonts.css")${fontCache?'+scope.css(scope.families())':''};
   html=html.replaceAll("(?i)inset\\\\s*:\\\\s*0(?:px)?\\\\s*;","top:0;right:0;bottom:0;left:0;inset:0;");
   ${prepared ? 'html=ImportedContent.prepare(html,clock,fonts,"window.__m2vToken=1;",false);' : `
   String added="<script>window.__m2vToken=1;window.__m2vReady=false;</script><script>"+clock+"</script><style>"+fonts+"</style>";
   html=html.replace("</head>",added+"</head>");
   ${oldResponse?'':'html=ImportedContent.secure(html,false);'}`}
   System.out.print(html);
   return;
  }
  for(int i=2;i<a.length;i++){
   int[] notices={0};
   WebResourceResponse r=StageAssets.response(c,Uri.parse(a[i])${oldResponse?'':',Boolean.parseBoolean(a[1])'}${fontNotice?',notice->notices[0]|=notice':''}${fontCache?',scope':''});
   if(r==null){System.out.println("network");continue;}
   try(InputStream data=r.data){System.out.println(r.status+" "+r.mime+" "+notices[0]+" "+Base64.getEncoder().encodeToString(data.readAllBytes()));}
  }
 }
}
`);
  const helpers = fontCache?['FontDiscovery.java','FontDownload.java','FontCache.java']:[];
  for(const name of helpers)fs.writeFileSync(path.join(tmp,name),get(ref,javaPath+name));
  const files = ['content/Context.java','net/Uri.java','webkit/WebResourceResponse.java'].map(name => path.join(contextSource,name));
  run('java',['-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',tmp,...files,'StageAssets.java','HostStage.java',...helpers,...(imports?[imports]:[])],{cwd:tmp,stdio:'inherit'});
  const launch=(...args)=>run('java',['-cp',tmp,'dev.motion2video.app.HostStage',assets,...args],{cwd:tmp,encoding:'utf8',maxBuffer:8*1024*1024});
  if(seed)launch('seed');
  const html=launch('document',input);
  const ready = prepared
    ? JSON.parse(get(ref,javaPath+'ImportedContent.java').match(/READY_SCRIPT\s*=\s*("(?:[^"\\]|\\.)*")/)[1])
    : JSON.parse(previewJava.match(/evaluateJavascript\(("(?:[^"\\]|\\.)*__m2vReady=true;(?:[^"\\]|\\.)*")/)[1]);
  function resolve(urls, online=false){
    return new Map(launch(String(online),...urls).trimEnd().split('\n').map((line,index)=>{
      if(line==='network')return [urls[index],null];
      const [status,contentType,notice,body]=line.split(' ');
      return [urls[index],{status:Number(status),contentType,body:Buffer.from(body||'','base64'),headers:{'access-control-allow-origin':'*'},notice:Number(notice)}];
    }));
  }
  return {assets,html,ready,resolve};
}

async function exercise(browser,stage,ref,exact,cacheState) {
  const remoteOrbitron = 'https://fonts.gstatic.com/test/orbitron.ttf';
  const remoteVazirmatn = 'https://fonts.gstatic.com/test/vazirmatn.ttf';
  const unknown = 'https://fonts.googleapis.com/css2?family=UnavailableFixture';
  const mixed = unknown + '&family=Orbitron';
  const cairo='https://fonts.googleapis.com/css2?family=Cairo';
  const cacheURLs=[...new Set(stage.html.match(/https:\/\/motion\.local\/cached-fonts\/[0-9a-f]{64}/g)||[])];
  const urls = [googleURL,unknown,mixed,remoteOrbitron,remoteVazirmatn,'https://untrusted.invalid/a.css',cairo,...cacheURLs];
  for(const name of fs.readdirSync(path.join(stage.assets,'fonts')).filter(s=>s.endsWith('.ttf'))){
    urls.push('https://motion.local/fonts/'+encodeURIComponent(name));
  }
  const policy=stage.resolve(urls);
  const localPaths=new Map([...policy].filter(([url])=>url.startsWith('https://motion.local/')).map(([url,response])=>[decodeURIComponent(new URL(url).pathname),response]));
  const context=await browser.newContext({viewport:{width:393,height:221},deviceScaleFactor:3,isMobile:true,hasTouch:true});
  try {
    const page=await context.newPage();
    const errors=[],blocked=[],violations=[],outbound=[];
    page.on('console',m=>{if(m.type()==='error')errors.push(m.text().split('\n')[0]);});
    page.on('pageerror',e=>errors.push(String(e)));
    page.on('requestfailed',r=>blocked.push([new URL(r.url()).host,r.failure()?.errorText]));
    await page.exposeFunction('reportTestCsp',(d,u)=>violations.push([d,u]));
    await page.addInitScript(()=>document.addEventListener('securitypolicyviolation',e=>window.reportTestCsp(e.effectiveDirective,e.blockedURI)));
    await page.route('**/*',async route=>{
      const url=route.request().url();
      if(url==='https://motion.local/')return route.fulfill({body:stage.html,contentType:'text/html; charset=utf-8'});
      const parsed=new URL(url);
      const match=parsed.origin==='https://motion.local'?localPaths.get(decodeURIComponent(parsed.pathname)):policy.get(url);
      if(match){
        if(match.status>=400)blocked.push([new URL(url).host,match.status]);
        const {notice,...response}=match;
        return route.fulfill(response);
      }
      outbound.push(new URL(url).host);
      // Isolated substitute for the network responses allowed by the old tag.
      // No request reaches Google or any other server during this test.
      const remote={headers:{'access-control-allow-origin':'*'}};
      if(url===googleURL)return route.fulfill({...remote,contentType:'text/css',body:
        "@font-face{font-family:Orbitron;src:url("+remoteOrbitron+");font-weight:400 900}@font-face{font-family:Vazirmatn;src:url("+remoteVazirmatn+");font-weight:100 900}"});
      if(url===remoteOrbitron)return route.fulfill({...remote,contentType:'font/ttf',path:process.env.M2V_TEST_ORBITRON||path.join(root,assetPath,'fonts/orbitron-Orbitron[wght].ttf')});
      if(url===remoteVazirmatn)return route.fulfill({...remote,contentType:'font/ttf',path:path.join(stage.assets,'fonts/vazirmatn-Vazirmatn[wght].ttf')});
      return route.abort();
    });
    const began=Date.now();
    await page.goto('https://motion.local/',{waitUntil:'load',timeout:8000});
    let readyMs = null, readyError = null;
    try {
      await page.evaluate(stage.ready);
      await page.waitForFunction(()=>window.__m2vReady===true,{},{timeout:4000});
      readyMs=Date.now()-began;
    } catch(error) {readyError=String(error).split('\n')[0];if(exact)throw error;}
    await page.waitForFunction(()=>parseFloat(document.querySelector('#progress').style.width)>0);
    const inspect=()=>page.evaluate(()=>{
      const c=document.getElementById('particles'), pixels=c.getContext('2d').getImageData(0,0,c.width,c.height).data;
      const covered=pixels.filter((v,i)=>i%4===3&&v).length;
      return {covered, size:[c.width,c.height], progress:parseFloat(document.querySelector('#progress').style.width),
        title:Number(getComputedStyle(document.querySelector('.active .big-title')).opacity),
        fonts:[...document.fonts].filter(f=>f.status==='loaded').map(f=>f.family)};
    });
    const first=await inspect();
    await page.waitForFunction(()=>Number(getComputedStyle(document.querySelector('.active .big-title')).opacity)>.9);
    const later=await inspect();
    const frameAcks=await page.evaluate(async()=>{
      const channel=new MessageChannel(), received=[];
      const expected=new Promise((resolve,reject)=>{
        const timer=setTimeout(()=>reject(Error('Clock handshake timed out')),2000);
        channel.port1.onmessage=e=>{
          received.push(e.data);
          if(e.data==='attached')window.__m2vSeek(.75,41);
          if(e.data==='41'){Object.defineProperty(document,'getAnimations',{value:undefined});window.__m2vSeek(1.5,42);}
          if(e.data==='42'){clearTimeout(timer);channel.port1.close();resolve(received);}
        };
      });
      window.postMessage('motion-clock-port-v1',location.origin,[channel.port2]);
      return expected;
    });
    const blank = !first.covered || !later.covered || later.title<.9;
    assert.deepEqual(frameAcks,['attached','41','42']);
    assert(later.progress>first.progress && !blank,'Timeline or Canvas did not advance');
    const noInput=await page.locator('.cursor').evaluate(e=>e.style.left==='');
    await page.mouse.move(68,57);
    await page.waitForFunction(()=>document.querySelector('.cursor').style.left==='68px');
    const baseline={errors:[...errors],blocked:[...blocked],violations:[...violations],outbound:[...outbound]};
    if(cacheState){
      assert.deepEqual(outbound,[]);
      assert.equal(policy.get(unknown).status,403);
      if(cacheState==='hit'){
        assert.deepEqual({errors,blocked,violations},{errors:[],blocked:[],violations:[]});
        assert(later.fonts.includes('Cairo'));
        assert.equal(policy.get(cairo).status,200);
        assert(cacheURLs.some(url=>policy.get(url).status===200));
      }else{
        assert.equal(policy.get(cairo).status,403);
        assert(!later.fonts.includes('Cairo'));
        assert(blocked.some(([host,status])=>host==='fonts.googleapis.com'&&status===403));
      }
    }
    if(exact){
      assert(noInput,'Unexpected cursor input before mouse event');
      assert.deepEqual({errors,blocked,violations,outbound},{errors:[],blocked:[],violations:[],outbound:[]});
      assert(later.fonts.includes('Orbitron')&&later.fonts.includes('Vazirmatn'),JSON.stringify(later));
      assert.equal(policy.get(unknown).status,403);
      assert.equal(policy.get(remoteOrbitron).status,403);
      assert.equal(policy.get(mixed).status,200);
      assert.equal(policy.get(googleURL).notice,1);
      assert.equal(policy.get(mixed).notice,3);
      assert.equal(policy.get(unknown).notice,2);
      assert.equal(policy.get('https://untrusted.invalid/a.css').status,403);
      await page.evaluate(url=>{
        const style=document.createElement('style');
        style.textContent="@font-face{font-family:externaltest;src:url("+url+")}.externalfonttest{font-family:externaltest}";
        document.head.append(style);
        const span=document.createElement('span');
        span.className='externalfonttest';
        span.textContent='A';
        document.body.append(span);
      },remoteOrbitron);
      await page.waitForFunction(()=>window.__m2vFontBlocked===true);
      assert(violations.some(([directive])=>directive==='font-src'));
      assert.deepEqual(outbound,[],'A direct blocked gstatic font reached the network');
      await page.evaluate(()=>Object.defineProperty(document,'fonts',{value:{ready:new Promise(()=>{})}}));
      await page.evaluate(stage.ready);
      await page.waitForFunction(()=>window.__m2vReady===true,{},{timeout:4500});
      console.log('PASS: direct gstatic CSP rejection and bounded font readiness.');
      const consent=stage.resolve([googleURL,unknown,remoteOrbitron],true);
      assert([...consent.values()].every(value=>value===null),'Online opt-in did not release Google fonts');
    }
    console.log(ref,JSON.stringify({readyMs,readyError, canvas:later.size, blank, fonts:[...new Set(later.fonts)],...baseline,frameAcks}));
  } finally {await context.close();}
}

(async()=>{
  const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||chromium.executablePath(),args:['--no-sandbox','--disable-dev-shm-usage']});
  try{
    for(const ref of process.argv.includes('--history')?refs:['worktree']){
      const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'m2v-fixture-'));
      try{await exercise(browser,makeStage(ref,tmp),ref,ref==='worktree'&&!process.argv.includes('--observe'));}
      finally{fs.rmSync(tmp,{recursive:true,force:true});}
    }
    if(!process.argv.includes('--history')){
      const variant=fixture.replace('@import url', "@import url('https://fonts.googleapis.com/css2?family=Cairo');\n  @import url")
        .replaceAll("'Orbitron',", "'Cairo', 'Orbitron',");
      for(const cacheState of ['miss','hit']){
        const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'m2v-cache-fixture-'));
        try{await exercise(browser,makeStage('worktree',tmp,variant,cacheState==='hit'),'Cairo '+cacheState,false,cacheState);}
        finally{fs.rmSync(tmp,{recursive:true,force:true});}
      }
    }
  }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
