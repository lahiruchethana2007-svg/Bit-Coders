const stockBody = document.getElementById("rows");

async function load() {
  const [products, stock] = await Promise.all([api("products"), api("stock")]);

  document.getElementById("productId").innerHTML = products
    .map(p => `<option value="${esc(p.id)}">${esc(p.name)}</option>`)
    .join("");

  if (stock.length === 0) {
    stockBody.innerHTML = `<tr><td colspan="4">No products yet. Add products first.</td></tr>`;
    return;
  }

  stockBody.innerHTML = stock.map(s => {
    const low = Number(s.quantity) <= Number(s.reorderLevel);
    return `<tr>
      <td>${esc(nameOf(products, s.productId))}</td>
      <td>${esc(s.quantity)}</td>
      <td><input class="small" type="number" min="0" value="${esc(s.reorderLevel)}" data-id="${esc(s.id)}"></td>
      <td>${low ? '<span class="badge low">Low stock</span>' : '<span class="badge ok">OK</span>'}</td>
    </tr>`;
  }).join("");
}

// Stock in from a supplier
document.getElementById("stockInForm").addEventListener("submit", event => {
  event.preventDefault();
  run(async () => {
    const form = event.target;
    await api("stock-in", "POST", Object.fromEntries(new FormData(form)));
    showMessage("Stock added.");
    form.reset();
    await load();
  });
});

// Change a reorder level
stockBody.addEventListener("change", event => {
  const id = event.target.dataset.id;
  if (!id) return;
  run(async () => {
    await api("stock", "POST", { id: id, reorderLevel: event.target.value });
    showMessage("Reorder level updated.");
    await load();
  });
});

run(load);
