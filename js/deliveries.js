const deliveryBody = document.getElementById("rows");

async function load() {
  const [orders, clients, agents, deliveries, outbox] = await Promise.all([
    api("orders"), api("clients"), api("agents"), api("deliveries"), api("outbox")
  ]);

  for (const [key,value] of Object.entries({metricTotal:deliveries.length,metricAssigned:deliveries.filter(d=>d.status==="Assigned").length,metricDispatched:deliveries.filter(d=>d.status==="Dispatched").length,metricDelivered:deliveries.filter(d=>d.status==="Delivered").length})) document.getElementById(key).textContent=value;
  const visible=deliveries.filter(d=>!document.getElementById("statusFilter").value||d.status===document.getElementById("statusFilter").value);
  // only orders that do not have an agent yet can be assigned
  const waiting = orders.filter(o => !deliveries.some(d => d.orderId === o.id));
  document.getElementById("orderId").innerHTML = waiting
    .map(o => `<option value="${esc(o.id)}">Order #${esc(o.id)} - ${esc(nameOf(clients, o.clientId))}</option>`)
    .join("");
  document.getElementById("agentId").innerHTML = agents
    .map(a => `<option value="${esc(a.id)}">${esc(a.name)}</option>`).join("");

  const dateBox = document.getElementById("scheduledDate");
  if (!dateBox.value) dateBox.value = new Date().toLocaleDateString("en-CA");

  deliveryBody.innerHTML = visible.length === 0
    ? `<tr><td colspan="6">No deliveries assigned yet.</td></tr>`
    : visible.map(d => {
        const order = orders.find(o => o.id === d.orderId);
        let action = "Done";
        if (d.status === "Assigned") action = `<button data-id="${esc(d.id)}" data-status="Dispatched">Mark dispatched</button>`;
        if (d.status === "Dispatched") action = `<button data-id="${esc(d.id)}" data-status="Delivered">Mark delivered</button>`;
        return `<tr>
          <td>#${esc(d.orderId)}</td>
          <td>${esc(order ? nameOf(clients, order.clientId) : "(deleted)")}</td>
          <td>${esc(nameOf(agents, d.agentId))}</td>
          <td>${esc(d.scheduledDate)}</td>
          <td>${esc(d.status)}</td>
          <td>${action}</td>
        </tr>`;
      }).join("");

  document.getElementById("emails").innerHTML = outbox.length === 0
    ? `<tr><td colspan="4">No emails yet.</td></tr>`
    : [...outbox].reverse().slice(0,5).map(m => `<tr>
        <td>${esc(m.sentAt)}</td><td>${esc(m.toAddress)}</td><td>${esc(m.subject)}</td><td><span class="status-chip ${m.status==="Sent"?"success":"warning"}">${esc(m.status||"Not configured")}</span></td>
      </tr>`).join("");
}

document.getElementById("assignForm").addEventListener("submit", event => {
  event.preventDefault();
  run(async () => {
    await api("deliveries", "POST", Object.fromEntries(new FormData(event.target)));
    showMessage("Delivery assigned. Check the notification result below or in the Notifications page.");
    await load();
  });
});

deliveryBody.addEventListener("click", event => {
  const id = event.target.dataset.id;
  const status = event.target.dataset.status;
  if (!id) return;
  run(async () => {
    await api("delivery-status", "POST", { id: id, status: status });
    showMessage(status === "Dispatched"
      ? "Marked as dispatched. Check the client email delivery result in Notifications."
      : "Marked as " + status.toLowerCase() + ".");
    await load();
  });
});

run(load);

document.getElementById("statusFilter").addEventListener("change",()=>run(load));
