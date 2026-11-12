let products = [];
let clients = [];
let lines = []; // items of the order being built: { productId, quantity }

function productOf(id) {
  return products.find(p => p.id === id);
}

function renderLines() {
  let total = 0;
  document.getElementById("lines").innerHTML = lines.map((line, index) => {
    const product = productOf(line.productId);
    const amount = Number(product.price) * line.quantity;
    total += amount;
    return `<li>${esc(product.name)} × ${line.quantity} = ${money(amount)}
      <button type="button" class="secondary" data-remove="${index}">Remove</button></li>`;
  }).join("");
  document.getElementById("total").textContent = "Total: " + money(total);
}

function itemsText(items) {
  return items.split(";").map(part => {
    const bits = part.split(":");
    return esc(nameOf(products, bits[0])) + " × " + esc(bits[1]);
  }).join(", ");
}

async function load() {
  [products, clients] = await Promise.all([api("products"), api("clients")]);
  const orders = await api("orders");

  document.getElementById("clientId").innerHTML = clients
    .map(c => `<option value="${esc(c.id)}">${esc(c.name)}</option>`).join("");
  document.getElementById("productId").innerHTML = products
    .map(p => `<option value="${esc(p.id)}">${esc(p.name)} (${money(p.price)})</option>`).join("");

  const body = document.getElementById("rows");
  if (orders.length === 0) {
    body.innerHTML = `<tr><td colspan="6">No orders yet.</td></tr>`;
    return;
  }
  body.innerHTML = [...orders].reverse().map(o => `<tr>
    <td>#${esc(o.id)}</td>
    <td>${esc(o.date)}</td>
    <td>${esc(nameOf(clients, o.clientId))}</td>
    <td>${itemsText(o.items)}</td>
    <td>${money(o.total)}</td>
    <td>${esc(o.status)}</td>
  </tr>`).join("");
}

document.getElementById("addItem").addEventListener("click", () => {
  const productId = document.getElementById("productId").value;
  const quantity = Number(document.getElementById("quantity").value);
  if (!productId || !Number.isInteger(quantity) || quantity < 1) {
    showMessage("Choose a product and a whole-number quantity.", true);
    return;
  }
  const existing = lines.find(l => l.productId === productId);
  if (existing) existing.quantity += quantity;
  else lines.push({ productId: productId, quantity: quantity });
  document.getElementById("message").className = "";
  renderLines();
});

document.getElementById("lines").addEventListener("click", event => {
  if (event.target.dataset.remove === undefined) return;
  lines.splice(Number(event.target.dataset.remove), 1);
  renderLines();
});

document.getElementById("placeOrder").addEventListener("click", () => {
  run(async () => {
    const items = lines.map(l => l.productId + ":" + l.quantity).join(";");
    const order = await api("orders", "POST", {
      clientId: document.getElementById("clientId").value,
      items: items
    });
    lines = [];
    renderLines();
    showMessage("Order #" + order.id + " placed. Total " + money(order.total) + ".");
    await load();
  });
});

run(load);
