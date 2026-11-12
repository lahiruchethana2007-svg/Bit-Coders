// Shared helpers used by every page.

const PAGES = [
  ["index.html", "Home"],
  ["products.html", "Products"],
  ["clients.html", "Clients"],
  ["stock.html", "Stock"],
  ["agents.html", "Delivery agents"],
  ["orders.html", "Orders"],
  ["deliveries.html", "Deliveries"],
  ["notifications.html", "Notifications"],
  ["reports.html", "Reports"]
];

// Talk to the Java server. Returns the data or throws an error with a readable message.
async function api(path, method, data) {
  const options = { method: method || "GET" };
  if (data) {
    options.headers = { "Content-Type": "application/x-www-form-urlencoded" };
    options.body = new URLSearchParams(data).toString();
  }
  const response = await fetch("/api/" + path, options);
  const result = await response.json();
  if (!response.ok) throw new Error(result.error || "Something went wrong.");
  return result;
}

// Run a task and show any error in the message box.
async function run(task) {
  try {
    await task();
  } catch (error) {
    showMessage(error.message, true);
  }
}

// Make text safe to put inside HTML.
function esc(text) {
  return String(text == null ? "" : text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function showMessage(text, isError) {
  const box = document.getElementById("message");
  box.textContent = text;
  box.className = isError ? "error" : "ok";
}

function money(value) {
  return "Rs. " + Number(value).toFixed(2);
}

// Find the name of a record (product, client, agent) from its id.
function nameOf(list, id) {
  const item = list.find(x => x.id === id);
  return item ? item.name : "(deleted)";
}

// True when the page was double-clicked instead of opened through the server.
const openedAsFile = location.protocol === "file:";

function renderNav() {
  const here = location.pathname.split("/").pop() || "index.html";
  const prefix = openedAsFile ? "" : "/html/";
  document.getElementById("nav").innerHTML = PAGES
    .map(p => `<a href="${prefix}${p[0]}" class="${p[0] === here ? "active" : ""}">${p[1]}</a>`)
    .join("");
}

// Pages need the Java server to load data. Tell the user how to start it.
function warnIfOpenedAsFile() {
  if (!openedAsFile) return;
  const note = document.createElement("div");
  note.className = "card";
  note.innerHTML = "<strong>This page was opened as a file, so it cannot load any data.</strong><br>" +
    "1. Run <code>run.bat</code> and keep the black window open.<br>" +
    "2. Open <a href=\"http://localhost:8080\">http://localhost:8080</a> in your browser.";
  document.querySelector("main").prepend(note);
}

// Builds a complete add / edit / remove page from a list of fields.
// Used by products, clients and delivery agents.
function crudPage(entity, fields) {
  const form = document.getElementById("form");
  const head = document.getElementById("head");
  const body = document.getElementById("rows");
  let rows = [];
  let editingId = "";

  const hasImage = entity === "products" || entity === "agents";
  form.innerHTML = fields.map(f => {
    const input = f.options
      ? `<select name="${f.name}">${f.options.map(o => `<option>${o}</option>`).join("")}</select>`
      : `<input name="${f.name}" type="${f.type || "text"}" ${f.attrs || ""} required>`;
    return `<label>${f.label}${input}</label>`;
  }).join("") + (hasImage ? `<label class="upload-field">Photo (optional)<input type="file" name="photoFile" accept="image/png,image/jpeg,image/webp"><small>JPG, PNG or WebP · max 3 MB</small><img id="imagePreview" class="upload-preview" alt="Selected photo preview" hidden></label>` : "") +
    `<div><button type="submit">Save</button> <button type="button" class="secondary" id="clear">Clear</button></div>`;

  head.innerHTML = "<tr>" + (hasImage ? "<th>Photo</th>" : "") + fields.map(f => `<th>${f.label}</th>`).join("") + "<th>Actions</th></tr>";

  async function load() {
    rows = await api(entity);
    if (rows.length === 0) {
      body.innerHTML = `<tr><td colspan="${fields.length + (hasImage ? 2 : 1)}">Nothing here yet. Use the form above to add the first record.</td></tr>`;
      return;
    }
    body.innerHTML = rows.map(r =>
      "<tr>" + (hasImage ? `<td>${r.imageUrl ? `<img class="table-photo" src="${esc(r.imageUrl)}" alt="${esc(r.name)}">` : `<span class="photo-placeholder">🌿</span>`}</td>` : "") + fields.map(f => `<td>${esc(r[f.name])}</td>`).join("") +
      `<td><button class="secondary" data-edit="${esc(r.id)}">Edit</button>` +
      `<button class="danger icon-delete" data-delete="${esc(r.id)}" title="Delete record" aria-label="Delete ${esc(r.name)}"><svg pointer-events="none" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18M8 6V4h8v2m3 0-1 14H6L5 6m5 4v7m4-7v7"/></svg></button></td></tr>`
    ).join("");
  }

  function clearForm() {
    form.reset();
    editingId = "";
    if (hasImage) {
      form.dataset.existingImage = "";
      const preview = document.getElementById("imagePreview");
      preview.hidden = true; preview.removeAttribute("src");
    }
  }

  if (hasImage) form.elements.photoFile.addEventListener("change", () => {
    const file = form.elements.photoFile.files[0];
    const preview = document.getElementById("imagePreview");
    if (!file) { preview.hidden = true; return; }
    preview.src = URL.createObjectURL(file); preview.hidden = false;
  });

  form.addEventListener("submit", event => {
    event.preventDefault();
    run(async () => {
      const data = Object.fromEntries(new FormData(form));
      if (hasImage) {
        delete data.photoFile;
        const file = form.elements.photoFile.files[0];
        if (file) {
          if (!["image/jpeg","image/png","image/webp"].includes(file.type)) throw new Error("Choose a JPG, PNG or WebP image.");
          if (file.size > 3 * 1024 * 1024) throw new Error("Photo must be under 3 MB.");
          const encoded = await new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result);
            reader.onerror = reject;
            reader.readAsDataURL(file);
          });
          const uploaded = await api("upload-image", "POST", {type:entity,image:encoded});
          data.imageUrl = uploaded.url;
        } else if (editingId) {
          data.imageUrl = form.dataset.existingImage || "";
        }
      }
      const wasEditing = editingId !== "";
      if (wasEditing) data.id = editingId;
      await api(entity, "POST", data);
      showMessage(wasEditing ? "Changes saved." : "Added.");
      clearForm();
      await load();
    });
  });

  document.getElementById("clear").addEventListener("click", clearForm);

  body.addEventListener("click", event => {
    const btn = event.target.closest("button");
    const editId = btn?.dataset.edit;
    const deleteId = btn?.dataset.delete;
    if (editId) {
      const row = rows.find(r => r.id === editId);
      fields.forEach(f => { form.elements[f.name].value = row[f.name] || ""; });
      if (hasImage) {
        form.elements.photoFile.value = "";
        form.dataset.existingImage = row.imageUrl || "";
        const preview = document.getElementById("imagePreview");
        preview.hidden = !row.imageUrl;
        if (row.imageUrl) preview.src = row.imageUrl;
      }
      editingId = editId;
      form.scrollIntoView();
    }
    if (deleteId && confirm("Remove this record?")) {
      run(async () => {
        await api(entity + "?id=" + deleteId, "DELETE");
        showMessage("Removed.");
        await load();
      });
    }
  });

  run(load);
}

renderNav();
warnIfOpenedAsFile();
