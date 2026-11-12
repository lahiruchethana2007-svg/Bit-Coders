const monthBox = document.getElementById("month");
monthBox.value = new Date().toLocaleDateString("en-CA").slice(0, 7);

async function showReport() {
  const r = await api("report?month=" + monthBox.value);

  const sold = r.productsSold.length === 0
    ? `<tr><td colspan="2">No sales in this month.</td></tr>`
    : r.productsSold.map(p => `<tr><td>${esc(p.name)}</td><td>${esc(p.units)}</td></tr>`).join("");

  const low = r.lowStock.length === 0
    ? `<tr><td colspan="3">Nothing is low on stock.</td></tr>`
    : r.lowStock.map(s => `<tr><td>${esc(s.name)}</td><td>${esc(s.quantity)}</td><td>${esc(s.reorderLevel)}</td></tr>`).join("");

  document.getElementById("report").innerHTML = `
    <div class="stats">
      <div><strong>${esc(r.orderCount)}</strong>Orders in ${esc(r.month)}</div>
      <div><strong>${money(r.revenue)}</strong>Revenue</div>
    </div>
    <div class="card table-wrap">
      <h3>Products sold</h3>
      <table><thead><tr><th>Product</th><th>Units sold</th></tr></thead><tbody>${sold}</tbody></table>
    </div>
    <div class="card table-wrap">
      <h3>Low-stock alerts</h3>
      <p class="note">Based on the stock you have right now.</p>
      <table><thead><tr><th>Product</th><th>On hand</th><th>Reorder level</th></tr></thead><tbody>${low}</tbody></table>
    </div>`;
}

document.getElementById("reportForm").addEventListener("submit", event => {
  event.preventDefault();
  run(showReport);
});

run(showReport);
