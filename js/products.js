crudPage("products", [
  { name: "name", label: "Product name" },
  { name: "type", label: "Type", options: ["Plant", "Planter", "Gardening kit"] },
  { name: "price", label: "Price (Rs.)", type: "number", attrs: 'min="0" step="0.01"' },
  { name: "ecoRating", label: "Eco-rating (1-5)", type: "number", attrs: 'min="1" max="5"' }
]);
