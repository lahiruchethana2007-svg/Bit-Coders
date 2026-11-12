// Home page: show how many records there are in each XML file.
const modules = [
  ["products", "Products", "products.html"],
  ["clients", "Clients", "clients.html"],
  ["agents", "Delivery agents", "agents.html"],
  ["orders", "Orders", "orders.html"]
];

run(async () => {
  const lists = await Promise.all(modules.map(m => api(m[0])));
  document.getElementById("stats").innerHTML = modules
    .map((m, i) => `<a href="/html/${m[2]}"><strong>${lists[i].length}</strong>${m[1]}</a>`)
    .join("");
});

run(async () => {
  const [products, agents] = await Promise.all([api("products"), api("agents")]);
  document.getElementById("homeProducts").innerHTML = products.length
    ? products.slice(0, 12).map(p => `<article class="product-tile"><div class="tile-image">${p.imageUrl ? `<img src="${esc(p.imageUrl)}" alt="${esc(p.name)}" loading="lazy">` : `<span class="empty-art">🌱</span>`}</div><div class="tile-info"><span class="tile-category">${esc(p.type || "Eco product")}</span><h4>${esc(p.name)}</h4><strong>${money(p.price)}</strong></div></article>`).join("")
    : '<p>No products yet. Add your first product in Products.</p>';
  document.getElementById("homeAgents").innerHTML = agents.length
    ? agents.slice(0, 12).map(a => `<article class="agent-tile">${a.imageUrl ? `<img src="${esc(a.imageUrl)}" alt="${esc(a.name)}" loading="lazy">` : `<span class="agent-avatar">👤</span>`}<div><h4>${esc(a.name)}</h4><p>${esc(a.vehicleType || "Delivery agent")}</p></div></article>`).join("")
    : '<p>No delivery agents yet.</p>';
});
