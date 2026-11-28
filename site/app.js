(function(){
var URL_="https://xzwlhegzneeujlpbjjba.supabase.co",KEY="sb_publishable_zIbBj3vtEGB5inLQDJGrAw_pwD4fVB-";
var root=document.getElementById("apps");
function esc(s){return String(s==null?"":s).replace(/[&<>"']/g,function(c){return{"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]})}
function safeUrl(u){u=String(u||"").trim();return /^https:\/\//i.test(u)?u:""}
function fmt(d){try{return new Date(d+"T00:00:00").toLocaleDateString("en-GB",{day:"numeric",month:"short",year:"numeric"})}catch(e){return d}}
function card(a){
var and=safeUrl(a.android_url),ios=safeUrl(a.ios_url),ic=safeUrl(a.icon_url);
var meta=[];
if(a.version)meta.push("<span>Version <b>"+esc(a.version)+"</b></span>");
if(a.model)meta.push("<span>Model <b>"+esc(a.model)+"</b></span>");
meta.push("<span>Released <b>"+esc(fmt(a.released_on))+"</b></span>");
meta.push("<span>Package <b>"+esc(a.package)+"</b></span>");
return '<article class="card"><div class="icon">'+(ic?'<img alt="" src="'+esc(ic)+'">':esc((a.name||"?").charAt(0).toUpperCase()))+'</div>'+
'<div><h2>'+esc(a.name)+'</h2><div class="meta">'+meta.join("")+'</div><p class="desc">'+esc(a.description)+'</p></div>'+
'<div class="stores"><div class="store"><div><small>Android</small><strong>APK download</strong></div>'+
(and?'<button class="btn" data-dl="'+esc(and)+'">Download</button>':'<span class="soon">Coming soon</span>')+'</div>'+
'<div class="store"><div><small>iOS</small><strong>iPhone and iPad</strong></div>'+
(ios?'<button class="btn" data-dl="'+esc(ios)+'">Download</button>':'<span class="soon">Coming soon</span>')+'</div></div></article>';
}
function load(){
fetch(URL_+"/rest/v1/site_apps?select=*&published=eq.true&order=created_at.desc",{headers:{apikey:KEY,Authorization:"Bearer "+KEY}})
.then(function(r){if(!r.ok)throw 0;return r.json()})
.then(function(rows){root.innerHTML=rows.length?rows.map(card).join(""):'<p class="note">Nothing published yet. Check back soon.</p>'})
.catch(function(){root.innerHTML='<p class="note">Could not load the apps right now. Please refresh in a moment.</p>'});
}
document.addEventListener("click",function(e){var b=e.target.closest("[data-dl]");if(b){window.location.href=b.getAttribute("data-dl")}});
if(root)load();
})();
