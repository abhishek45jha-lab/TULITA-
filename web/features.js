const WEB_CATALOG_URL = 'https://raw.githubusercontent.com/abhishek45jha-lab/TULITA-/main/datasets/promap/catalog.json';
const WEB_ASSET_ROOT = 'https://raw.githubusercontent.com/abhishek45jha-lab/TULITA-/main/datasets/promap/';
let featureCatalog = [];
let activeFeature = 'catalog';
const featureMoney = (value, currency='USD') => new Intl.NumberFormat(undefined,{style:'currency',currency,maximumFractionDigits:2}).format(value);
const safeText = value => String(value ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const webPhoto = path => WEB_ASSET_ROOT + String(path||'').split('/').map(encodeURIComponent).join('/');

function installFeaturePanels(){
  const main=document.querySelector('main'), hero=main.querySelector('.hero');
  const tabs=document.createElement('nav'); tabs.className='feature-tabs'; tabs.setAttribute('aria-label','Tulita tools');
  tabs.innerHTML='<button data-tab="catalog" class="selected">Discover</button><button data-tab="budget">◎ Budget planner</button><button data-tab="photo">⌗ Photo search</button>';
  const originalNodes=[main.querySelector('#catalog'),main.querySelector('.results-row'),main.querySelector('#product-grid'),main.querySelector('#status')];
  const catalog=document.createElement('section');catalog.id='catalog-panel';catalog.className='feature-panel';originalNodes.forEach(node=>catalog.append(node));
  const budget=document.createElement('section');budget.id='budget-panel';budget.className='feature-panel';
  const photo=document.createElement('section');photo.id='photo-panel';photo.className='feature-panel';
  budget.innerHTML=`<div class="feature-heading"><span class="eyebrow">PLAN A BASKET</span><h2>Budget planner</h2><p>Choose categories and compare a basket against the catalog's recorded offers. Delivery and discount inputs are your estimates.</p></div>
    <div class="planner-controls"><label class="field-label">Budget <span class="money-input"><input id="plan-budget" type="number" min="0.01" step="0.01" value="200"><select id="plan-currency" aria-label="Planning currency"><option value="USD">USD · catalog currency</option><option value="INR">INR · enter your rate</option></select></span></label>
    <label class="field-label rate-field" hidden>INR per USD (your estimate)<input id="plan-rate" type="number" min="0.0001" step="0.01" value="83.50"></label>
    <label class="field-label">Objective<select id="plan-mode"><option value="value">Best value (price + specs)</option><option value="cost">Lowest cost</option><option value="specs">Most documented specifications</option><option value="balanced">Balanced</option></select></label>
    <label class="field-label">Delivery fee per store <span class="muted-hint">your estimate · USD</span><div class="fee-row"><input aria-label="Amazon delivery fee" data-fee="Amazon" type="number" min="0" step="0.01" placeholder="Amazon" value="0"><input aria-label="Walmart delivery fee" data-fee="Walmart" type="number" min="0" step="0.01" placeholder="Walmart" value="0"><input aria-label="Target delivery fee" data-fee="Target" type="number" min="0" step="0.01" placeholder="Target" value="0"></div></label>
    <div class="discount-row"><label class="field-label">Discount % <input id="plan-discount" type="number" min="0" max="100" step="0.1" value="0"></label><label class="field-label">Minimum item subtotal (USD) <input id="plan-threshold" type="number" min="0" step="0.01" value="0"></label></div>
    </div><div class="category-picker"><div class="picker-heading"><h3>Categories to include</h3><span>Each selected category requires one product variant.</span></div><div id="plan-categories" class="category-options">Loading categories…</div></div>
    <button id="run-plan" class="primary-action">Optimize my budget <span>→</span></button><div id="plan-result" class="plan-result" aria-live="polite"></div>
    <p class="feature-disclaimer">Browser planner is a simplified companion to the Java desktop optimizer. It uses catalog prices/specification coverage, supports one selected product per selected category and reports its search cap; it is not the identical Java Pareto optimizer. Historical prices are not live.</p>`;
  photo.innerHTML=`<div class="feature-heading"><span class="eyebrow">ON-DEVICE PREVIEW</span><h2>Search by photo</h2><p>Choose a product picture. It stays in this browser; this page does not upload it.</p></div><label class="upload-zone" for="photo-file"><span class="upload-symbol">＋</span><strong>Choose or drop a product image</strong><span>JPG, PNG, GIF or WebP · processed locally in your browser</span><input id="photo-file" type="file" accept="image/*"></label><div id="photo-preview" class="photo-preview"></div><div id="photo-status" class="photo-status" role="status">Select an image to see visually similar catalog photos.</div><div id="photo-matches" class="photo-matches"></div><p class="feature-disclaimer">This browser preview ranks reference photos by coarse color-histogram similarity; it is not the desktop CLIP/ONNX model and cannot verify product identity or exact variant. Confirm any suggestion with product text and specifications. Reference photos load from the public repository.</p>`;
  hero.after(tabs,catalog,budget,photo);
  tabs.addEventListener('click',event=>{const btn=event.target.closest('[data-tab]');if(btn)activateFeature(btn.dataset.tab)});
  document.querySelectorAll('[data-feature-tab]').forEach(link=>link.addEventListener('click',()=>activateFeature(link.dataset.featureTab)));
  document.querySelector('#plan-currency').addEventListener('change',event=>{document.querySelector('.rate-field').hidden=event.target.value!=='INR'});
  document.querySelector('#run-plan').addEventListener('click',runBrowserPlan);
  const upload=document.querySelector('#photo-file');
  upload.addEventListener('change',()=>{if(upload.files[0])searchByPhoto(upload.files[0])});
  const zone=document.querySelector('.upload-zone');
  zone.addEventListener('dragover',event=>{event.preventDefault();zone.classList.add('dragging')});
  zone.addEventListener('dragleave',()=>zone.classList.remove('dragging'));
  zone.addEventListener('drop',event=>{event.preventDefault();zone.classList.remove('dragging');const file=event.dataTransfer.files[0];if(file?.type.startsWith('image/'))searchByPhoto(file)});
  document.querySelector('#sidebar-count').textContent='79';
}
function activateFeature(tab){activeFeature=tab;document.querySelectorAll('.feature-panel').forEach(panel=>panel.classList.toggle('feature-active',panel.id===({catalog:'catalog-panel',budget:'budget-panel',photo:'photo-panel'}[tab])));document.querySelectorAll('.feature-tabs [data-tab]').forEach(button=>button.classList.toggle('selected',button.dataset.tab===tab));document.querySelectorAll('[data-feature-tab]').forEach(link=>link.classList.toggle('active',link.dataset.featureTab===tab));document.querySelector('.topbar .eyebrow').textContent=`TULITA / ${tab==='photo'?'PHOTO SEARCH':tab==='budget'?'BUDGET PLANNER':'DISCOVER'}`;if(tab==='budget'&&!document.querySelector('#plan-categories').dataset.ready)renderCategoryChoices();}
function renderCategoryChoices(){const categories=[...new Set(featureCatalog.map(p=>p.category).filter(Boolean))].sort();document.querySelector('#plan-categories').innerHTML=categories.map(category=>`<label class="category-option"><input type="checkbox" value="${safeText(category)}"><span>${safeText(category)}</span><small>${featureCatalog.filter(p=>p.category===category).length} variants</small></label>`).join('');document.querySelector('#plan-categories').dataset.ready='true'}

function runBrowserPlan(){
 const result=document.querySelector('#plan-result');
 const budgetInput=Number(document.querySelector('#plan-budget').value), currency=document.querySelector('#plan-currency').value, rate=Number(document.querySelector('#plan-rate').value);
 if(!(budgetInput>0)){result.innerHTML='<p class="result-error">Enter a budget greater than zero.</p>';return}
 if(currency==='INR'&&!(rate>0)){result.innerHTML='<p class="result-error">Enter your own positive INR-per-USD rate.</p>';return}
 const budget=currency==='INR'?budgetInput/rate:budgetInput;
 const categories=[...document.querySelectorAll('#plan-categories input:checked')].map(input=>input.value);
 if(!categories.length){result.innerHTML='<p class="result-error">Select at least one category to plan a basket.</p>';return}
 const mode=document.querySelector('#plan-mode').value, discount=Math.max(0,Math.min(100,Number(document.querySelector('#plan-discount').value)||0))/100, threshold=Number(document.querySelector('#plan-threshold').value)||0;
 const fees=Object.fromEntries([...document.querySelectorAll('[data-fee]')].map(input=>[input.dataset.fee,Math.max(0,Number(input.value)||0)]));
 const optionsByCategory=categories.map(category=>{
   const rows=[];
   featureCatalog.filter(p=>p.category===category).forEach(product=>{
     const specCount=product.offers.reduce((max,offer)=>{try{return Math.max(max,JSON.parse(offer.specifications||'[]').filter(x=>x.key&&x.value).length)}catch{return max}},0);
     product.offers.forEach(offer=>{const price=Number(offer.price);if(price>0&&Number.isFinite(price)){const peer=product.offers.map(x=>Number(x.price)).filter(Number.isFinite);const low=Math.min(...peer),high=Math.max(...peer);const priceScore=high===low?1:(high-price)/(high-low);rows.push({product,offer,price,specCount,utility:.7*priceScore+.3*Math.min(1,specCount/8),store:offer.store})}})
   });
   return rows;
 });
 if(optionsByCategory.some(rows=>!rows.length)){result.innerHTML='<p class="result-error">A selected category has no usable recorded price.</p>';return}
 const feeByStore=fees;
 let states=[{subtotal:0,stores:new Set(),utility:0,choices:[]}], capped=false, evaluated=1;
 for(let ci=0;ci<categories.length;ci++){
   const next=[];
   for(const state of states)for(const option of optionsByCategory[ci]){
     const subtotal=state.subtotal+option.price;
     const stores=new Set(state.stores);stores.add(option.store);
     const shipping=[...stores].reduce((sum,store)=>sum+(feeByStore[store]||0),0);
     const discountAmount=subtotal>=threshold?subtotal*discount:0;
     if(subtotal+shipping-discountAmount>budget+1e-8)continue;
     next.push({subtotal,stores,utility:state.utility+option.utility,choices:[...state.choices,option]});
   }
   evaluated+=next.length;
   const grouped=new Map();
   for(const state of next){const key=[...state.stores].sort().join('|');const bucket=grouped.get(key)||[];let dominated=false;for(const old of bucket){if(old.subtotal<=state.subtotal&&old.utility>=state.utility){dominated=true;break}}if(!dominated){grouped.set(key,bucket.filter(old=>!(state.subtotal<=old.subtotal&&state.utility>=old.utility)).concat(state))}}
   states=[...grouped.values()].flat();
   if(states.length>5000){states.sort((a,b)=>b.utility-a.utility);states=states.slice(0,5000);capped=true}
   if(!states.length)break;
 }
 if(!states.length){result.innerHTML=`<p class="result-error">No basket fits this budget for every selected category. Raise the budget, reduce delivery estimates, or select fewer categories.</p>`;return}
 const comparator={cost:(a,b)=>a.total-b.total||b.utility-a.utility,specs:(a,b)=>b.specs-a.specs||a.total-b.total,balanced:(a,b)=>((.6*b.utility/categories.length+.4*(1-b.total/budget))-(.6*a.utility/categories.length+.4*(1-a.total/budget)))||a.total-b.total,value:(a,b)=>b.utility-a.utility||a.total-b.total}[mode];
 const completed=states.map(state=>{const shipping=[...state.stores].reduce((sum,store)=>sum+(feeByStore[store]||0),0),discountAmount=state.subtotal>=threshold?state.subtotal*discount:0;return{...state,shipping,discountAmount,total:state.subtotal+shipping-discountAmount,specs:state.choices.reduce((s,x)=>s+x.specCount,0)}}).sort(comparator);
 const best=completed[0], shown=currency==='INR'?x=>featureMoney(x*rate,'INR'):x=>featureMoney(x,'USD');
 result.innerHTML=`<div class="result-header"><div><span class="eyebrow">RECOMMENDED BASKET · ${safeText(mode.toUpperCase())}</span><h3>${shown(best.total)}</h3><p>${currency==='INR'?`${featureMoney(best.total,'USD')} catalog total · your rate ${rate} INR/USD`:`${featureMoney(budget-best.total,currency)} remaining of ${featureMoney(budget,currency)}`}</p></div><span class="result-badge">${best.choices.length} categories</span></div><div class="cost-breakdown"><span>Items <b>${shown(best.subtotal)}</b></span><span>Delivery estimate <b>+ ${shown(best.shipping)}</b></span><span>Discount estimate <b>− ${shown(best.discountAmount)}</b></span></div><div class="basket-lines">${best.choices.map(choice=>`<article class="basket-line"><img loading="lazy" src="${webPhoto(choice.product.photos?.[0]?.path||'')}" alt="" onerror="this.style.visibility='hidden'"><span><small>${safeText(choice.product.category)} · ${safeText(choice.offer.store)}</small><strong>${safeText(choice.product.name)}</strong><small>Recorded item price · ${safeText(choice.product.variant||'')}</small></span><b>${shown(choice.price)}</b><a href="${safeText(choice.offer.url)}" target="_blank" rel="noopener noreferrer" aria-label="Open retailer listing">↗</a></article>`).join('')}</div><p class="planner-note">${capped?'Search reached its 5,000-state safety cap; this basket is a valid bounded result, not a proven global optimum. ':`Examined ${evaluated.toLocaleString()} candidate states. `}One recorded offer is selected per category. Fees and discounts are user estimates; no stock or current price is checked.</p>`;
}

function histogram(image){const canvas=document.createElement('canvas'),size=32;canvas.width=size;canvas.height=size;const ctx=canvas.getContext('2d',{willReadFrequently:true});ctx.drawImage(image,0,0,size,size);const data=ctx.getImageData(0,0,size,size).data,bins=new Float32Array(24);for(let i=0;i<data.length;i+=4){if(data[i+3]<100)continue;const r=data[i]/255,g=data[i+1]/255,b=data[i+2]/255;bins[Math.min(7,Math.floor(r*8))]++;bins[8+Math.min(7,Math.floor(g*8))]++;bins[16+Math.min(7,Math.floor(b*8))]++}const total=bins.reduce((a,b)=>a+b,0)||1;return bins.map(x=>x/total)}
function loadReference(path){return new Promise((resolve,reject)=>{const image=new Image();image.crossOrigin='anonymous';image.onload=()=>{try{resolve(histogram(image))}catch(error){reject(error)}};image.onerror=()=>reject(new Error('Reference image could not be loaded'));image.src=webPhoto(path)})}
async function searchByPhoto(file){
 const status=document.querySelector('#photo-status'),matches=document.querySelector('#photo-matches'),preview=document.querySelector('#photo-preview');matches.innerHTML='';status.textContent='Analyzing image locally and comparing reference photos…';
 const localUrl=URL.createObjectURL(file);preview.innerHTML=`<img src="${localUrl}" alt="Your selected image"><span>Query image · stays in this browser</span>`;
 try{const source=await createImageBitmap(file),query=histogram(source);source.close?.();const refs=[];for(const product of featureCatalog){for(const photo of product.photos||[]){if(refs.length>=100)break;refs.push({product,path:photo.path})}if(refs.length>=100)break}
   const scores=[];let cursor=0;async function worker(){while(cursor<refs.length){const item=refs[cursor++];try{const hist=await loadReference(item.path);let distance=0;for(let i=0;i<hist.length;i++)distance+=Math.abs(hist[i]-query[i]);scores.push({product:item.product,similarity:1-distance/2})}catch{/* Cross-origin or unavailable reference images are skipped. */}}}await Promise.all(Array.from({length:6},worker));
   const best=new Map();for(const score of scores){const existing=best.get(score.product.id);if(!existing||score.similarity>existing.similarity)best.set(score.product.id,score)}const ranked=[...best.values()].sort((a,b)=>b.similarity-a.similarity).slice(0,5);
   if(!ranked.length){status.textContent='Reference images could not be compared from this hosting environment. Try text search in Discover.';return}status.textContent=`Top ${ranked.length} visual-lookalike suggestions from ${scores.length} reference photos. Similarity is not product identity.`;
   matches.innerHTML=ranked.map((item,index)=>`<button class="photo-match" data-match="${safeText(item.product.id)}"><img loading="lazy" src="${webPhoto(item.product.photos?.[0]?.path||'')}" alt="" onerror="this.style.visibility='hidden'"><span><small>VISUAL SUGGESTION ${index+1} · ${Math.round(item.similarity*100)}% histogram similarity</small><strong>${safeText(item.product.name)}</strong><small>${safeText(item.product.variant||item.product.category||'')}</small></span><b>→</b></button>`).join('');
 }catch(error){console.warn(error);status.textContent='This browser could not analyze that image. Choose a standard JPG or PNG, or search by text in Discover.'}
}

document.addEventListener('click',event=>{const match=event.target.closest('[data-match]');if(!match)return;const product=featureCatalog.find(item=>item.id===match.dataset.match);if(!product)return;activateFeature('catalog');document.querySelector('#search').value=product.name;document.querySelector('#search').dispatchEvent(new Event('input',{bubbles:true}));window.scrollTo({top:0,behavior:'smooth'})});
installFeaturePanels();
fetch(WEB_CATALOG_URL).then(response=>{if(!response.ok)throw new Error(`Catalog HTTP ${response.status}`);return response.json()}).then(data=>{featureCatalog=data;document.querySelector('#sidebar-count').textContent=String(data.length);document.querySelector('#stat-products').textContent=String(data.length);document.querySelector('#stat-prices').textContent=String(data.reduce((sum,p)=>sum+(p.offers?.length||0),0));if(activeFeature==='budget')renderCategoryChoices()}).catch(error=>{console.warn('Feature catalog unavailable',error);document.querySelector('#plan-categories').textContent='Catalog unavailable; refresh the page and try again.';document.querySelector('#photo-status').textContent='Catalog could not be loaded for photo matching.'});
