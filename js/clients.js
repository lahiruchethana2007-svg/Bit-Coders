crudPage("clients", [
  { name: "name", label: "Client name" },
  { name: "type", label: "Type", options: ["Household", "Office"] },
  { name: "phone", label: "Phone" },
  { name: "email", label: "Email", type: "email" },
  { name: "address", label: "Address" }
]);
