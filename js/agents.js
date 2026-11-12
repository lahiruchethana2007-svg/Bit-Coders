crudPage("agents", [
  { name: "name", label: "Agent name" },
  { name: "phone", label: "Phone" },
  { name: "email", label: "Email", type: "email" },
  { name: "vehicleType", label: "Vehicle type", options: ["Bike", "Three-wheeler", "Van", "Lorry"] },
  { name: "vehicleNumber", label: "Vehicle number" }
]);
