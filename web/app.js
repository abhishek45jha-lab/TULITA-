const CATALOG_URL = 'https://raw.githubusercontent.com/abhishek45jha-lab/TULITA-/main/datasets/promap/catalog.json';
const ASSET_ROOT = 'https://raw.githubusercontent.com/abhishek45jha-lab/TULITA-/main/datasets/promap/';

const grid = document.querySelector('#product-grid');
const statusBox = document.querySelector('#status');
const searchInput = document.querySelector('#search');
const categorySelect = document.querySelector('#category');
const sortSelect = document.querySelector('#sort');
const dialog = document.querySelector('#compare-dialog');
const dialogContent = document.querySelector('#dialog-content');
let products = [];

const money = (amount, currency = 'USD') => {
  const value = Number(amount);
  return Number.isFinite(value) ? new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value) : 'Price unavailable';
};
const escapeHtml = (value = '') => String(value).replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
const imageUrl = product => {
  const photo = product.photos?.[0]?.path;
  return photo ? ASSET_ROOT + photo.split('/').map(encodeURIComponent).join('/') : '';
};
const lowest = product => Math.min(...(product.offers || []).map(offer => Number(offer.price)).filter(Number.isFinite));

function render() {
  const term = searchInput.value.trim().toLocaleLowerCase();
  const category = categorySelect.value;
  const selected = products.filter(product => {
    const haystack = [product.name, product.brand, product.model, product.variant, product.category, ...(product.offers || []).map(offer => offer.store)].join(' ').toLocaleLowerCase();
    return (!term || haystack.includes(term)) && (!category || product.category === category);
  });
  if (sortSelect.value === 'low') selected.sort((a, b) => lowest(a) - lowest(b));
  if (sortSelect.value === 'high') selected.sort((a, b) => lowest(b) - lowest(a));
  if (sortSelect.value === 'name') selected.sort((a, b) => a.name.localeCompare(b.name));
  document.querySelector('#results-count').textContent = `${selected.length} ${selected.length === 1 ? 'product' : 'products'} found`;
  grid.innerHTML = selected.map((product, index) => {
    const photo = imageUrl(product);
    const price = lowest(product);
    const currency = product.offers?.[0]?.currency || 'USD';
    const stores = [...new Set((product.offers || []).map(offer => offer.store))];
    return `<article class="product-card" style="--i:${index % 12}">
      <button class="product-open" data-product="${escapeHtml(product.id)}" aria-label="Compare ${escapeHtml(product.name)}">
        <div class="product-image">${photo ? `<img loading="lazy" src="${photo}" alt="${escapeHtml(product.name)}" onerror="this.closest('.product-image').classList.add('image-missing');this.remove()">` : ''}<span class="store-count">${stores.length} ${stores.length === 1 ? 'store' : 'stores'}</span></div>
        <div class="product-info"><span class="category-label">${escapeHtml(product.category || 'Product')}</span><h3>${escapeHtml(product.name)}</h3><p class="variant">${escapeHtml(product.variant || product.model || '')}</p>
        <div class="card-bottom"><span><small>LOWEST RECORDED</small><strong>${money(price, currency)}</strong></span><span class="compare-cta">Compare <b>↗</b></span></div>
        <div class="store-chips">${stores.map(store => `<span>${escapeHtml(store)}</span>`).join('')}</div></div>
      </button></article>`;
  }).join('');
  statusBox.hidden = selected.length > 0;
  if (!selected.length) statusBox.textContent = 'No products match. Try another search or category.';
}

function showComparison(productId) {
  const product = products.find(item => item.id === productId);
  if (!product) return;
  const photo = imageUrl(product);
  const offers = product.offers || [];
  const low = lowest(product);
  const currency = offers[0]?.currency || 'USD';
  const sortedOffers = [...offers].sort((a, b) => Number(a.price) - Number(b.price));
  dialogContent.innerHTML = `<span class="eyebrow">PRODUCT COMPARISON</span><div class="dialog-product">${photo ? `<img src="${photo}" alt="${escapeHtml(product.name)}" onerror="this.remove()">` : ''}<div><span class="category-label">${escapeHtml(product.category || 'Product')}</span><h2 id="dialog-title">${escapeHtml(product.name)}</h2><p>${escapeHtml(product.variant || '')}</p><small>${escapeHtml(product.brand || '')}${product.model && product.model !== 'Not provided' ? ` · Model ${escapeHtml(product.model)}` : ''}</small></div></div>
    <div class="comparison-summary"><div><small>LOWEST RECORDED</small><strong>${money(low, currency)}</strong></div><div><small>PRICE SPREAD</small><strong>${money(Math.max(...offers.map(o => Number(o.price))) - low, currency)}</strong></div><div><small>STORES</small><strong>${offers.length}</strong></div></div>
    <h3 class="section-title">Recorded offers</h3><div class="offer-list">${sortedOffers.map((offer, index) => `<div class="offer-row"><span class="offer-store"><i class="store-dot store-${escapeHtml(offer.store.toLowerCase())}"></i>${escapeHtml(offer.store)}</span><span class="offer-title">${escapeHtml(offer.title || '')}</span><strong>${money(offer.price, offer.currency || currency)}</strong><a href="${escapeHtml(offer.url || '#')}" target="_blank" rel="noopener noreferrer" aria-label="Open ${escapeHtml(offer.store)} listing">↗</a>${index === 0 ? '<span class="lowest-tag">LOWEST</span>' : ''}</div>`).join('')}</div>
    <p class="history-note">These are retained historical source amounts, not live prices or a guarantee of current availability. Check the retailer listing for current details.</p>
    <details class="evidence"><summary>Source review & evidence</summary><p>${escapeHtml(product.reviewNote || 'Source record details are included with the project data.')}</p><p>Source dataset: ${escapeHtml(offers[0]?.sourceFile || 'curated catalog')}. Amounts are shown as supplied in the retained records.</p></details>`;
  dialog.showModal();
}

async function init() {
  try {
    const response = await fetch(CATALOG_URL);
    if (!response.ok) throw new Error(`Catalog request returned ${response.status}`);
    products = await response.json();
    if (!Array.isArray(products)) throw new Error('Catalog format was not recognized');
    const categories = [...new Set(products.map(product => product.category).filter(Boolean))].sort((a, b) => a.localeCompare(b));
    categorySelect.insertAdjacentHTML('beforeend', categories.map(category => `<option value="${escapeHtml(category)}">${escapeHtml(category)}</option>`).join(''));
    document.querySelector('#stat-products').textContent = products.length;
    document.querySelector('#stat-prices').textContent = products.reduce((sum, product) => sum + (product.offers?.length || 0), 0);
    statusBox.hidden = true;
    render();
  } catch (error) {
    console.error(error);
    statusBox.hidden = false;
    statusBox.innerHTML = '<strong>Catalog could not be loaded.</strong><br>Please refresh, or check whether raw.githubusercontent.com is accessible from your network.';
    document.querySelector('#results-count').textContent = 'Catalog unavailable';
  }
}

searchInput.addEventListener('input', render);
categorySelect.addEventListener('change', render);
sortSelect.addEventListener('change', render);
grid.addEventListener('click', event => {
  const button = event.target.closest('[data-product]');
  if (button) showComparison(button.dataset.product);
});
document.addEventListener('keydown', event => {
  if (event.key === '/' && !['INPUT', 'SELECT', 'TEXTAREA'].includes(document.activeElement.tagName)) { event.preventDefault(); searchInput.focus(); }
  if (event.key === 'Escape' && dialog.open) dialog.close();
});
dialog.addEventListener('click', event => { if (event.target === dialog) dialog.close(); });
init();
